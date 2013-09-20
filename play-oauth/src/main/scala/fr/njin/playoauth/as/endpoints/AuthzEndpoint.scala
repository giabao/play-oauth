package fr.njin.playoauth.as.endpoints

import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future}
import play.api.data.Form
import fr.njin.playoauth.common.OAuth
import play.api.i18n.Messages
import fr.njin.playoauth.common.client._
import java.util.UUID
import fr.njin.playoauth.common
import scala.util.Either
import scala.Predef._
import scala.Some
import play.api.mvc.SimpleResult
import Results._
import play.api.http.Status._

/**
 * User: bathily
 * Date: 17/09/13
 */
class AuthzEndpoint[I <: OauthClientInfo,T <: OauthClient, SC <: OauthScope](
  clientFactory: OauthClientFactory[I , T],
  clientRepository: OauthClientRepository[T],
  scopeRepository: OauthScopeRepository[SC]
) extends common.Logger {

  type AuthzValidation =  (AuthzRequest, OauthClient) => ExecutionContext => Future[Either[Boolean, Map[String, Seq[String]]]]

  val responseTypeCodeValidation:AuthzValidation = (authzRequest, client) => implicit ec => { Future.successful {
    Some(OAuth.ResponseType.All.contains(authzRequest.responseType)).filter(_ == true).toLeft(Map(OAuth.OauthError -> Seq(OAuth.ErrorCode.UnsupportedResponseType)))
  }}

  val scopeValidation:AuthzValidation = (authzRequest, client) => implicit ec => {
    authzRequest.scope.fold[Future[Either[Boolean, Map[String, Seq[String]]]]](Future.successful(Left(true))){ scope =>
      scopeRepository.find(scope : _*).map{ scopes =>
        val errors = scopes.filterNot(_._2.isDefined)
        Some(errors.isEmpty).filter(_ == true).toLeft(Map(
          OAuth.OauthError -> Seq(OAuth.ErrorCode.InvalidScope),
          OAuth.OauthErrorDescription -> Seq(Messages(OAuth.ErrorInvalidScope, errors.map(e => e._1).mkString(" ")))
        ))
      }
    }
  }

  val clientAuthorizedValidation:AuthzValidation = (authzRequest, client) => implicit ec => {Future.successful {
    Some(client.authorized).filter(_ == true).toLeft(Map(OAuth.OauthError -> Seq(OAuth.ErrorCode.AccessDenied)))
  }}

  val clientResponseTypeValidation:AuthzValidation = (authzRequest, client) => implicit ec => { Future.successful {
    Some(client.allowedResponseType.contains(authzRequest.responseType)).filter(_ == true).toLeft(Map(
      OAuth.OauthError -> Seq(OAuth.ErrorCode.UnauthorizedClient),
      OAuth.OauthErrorDescription -> Seq(Messages(OAuth.ErrorUnauthorizedResponseType, authzRequest.responseType))
    ))
  }}


  val authzValidator = Seq(responseTypeCodeValidation, scopeValidation, clientAuthorizedValidation, clientResponseTypeValidation)


  def errorToQuery(f:Form[_]):Map[String, Seq[String]] = queryWithState(Map(
    OAuth.OauthError -> Seq(OAuth.ErrorCode.InvalidRequest),
    OAuth.OauthErrorDescription -> Seq(f.errorsAsJson.toString())
  ), f(OAuth.OauthState).value)

  def queryWithState(query: Map[String, Seq[String]], state:Option[String]):Map[String, Seq[String]] =
     state.map(s => query + (OAuth.OauthState -> Seq(s))).getOrElse(query)

  def register(allowedResponseType: Seq[String], info:I)(implicit ec:ExecutionContext): Future[T] = {
    clientFactory.apply(allowedResponseType, info).flatMap(clientRepository.save)
  }

  def deRegister(client:T)(implicit ec:ExecutionContext): Future[Unit] = {
    clientRepository.delete(client)
  }

  def onFormError(f:Form[AuthzRequest])(implicit request:Request[AnyContent], ec:ExecutionContext) = {
    f.error(OAuth.OauthClientId).map(e => Future.successful(BadRequest(Messages(OAuth.ErrorClientMissing))))
      .orElse(f.error(OAuth.OauthRedirectUri).map(e => Future.successful(BadRequest(Messages(OAuth.ErrorRedirectURIInvalid, e.args)))))
      .getOrElse {
      val id = f(OAuth.OauthClientId).value.get
      clientRepository.find(id).map(_.fold(NotFound(Messages(OAuth.ErrorClientNotFound, id))) { client =>
        client.redirectUri.orElse(f(OAuth.OauthRedirectUri).value).fold(BadRequest(Messages(OAuth.ErrorRedirectURIMissing))) { url =>
          Redirect(url, errorToQuery(f), FOUND)
        }
      })
    }
  }

  def onAuthzRequest(authzRequest: AuthzRequest)(f:(AuthzRequest, OauthClient) => Request[AnyContent] => Future[SimpleResult])(implicit request:Request[AnyContent], ec:ExecutionContext) = {
    clientRepository.find(authzRequest.clientId).flatMap{ _.fold(Future.successful(NotFound(Messages(OAuth.ErrorClientNotFound, authzRequest.clientId)))){ client =>
      val url = client.redirectUri.orElse(authzRequest.redirectUri).get
      Future.find(authzValidator.map(_(authzRequest, client)(ec)))(_.isRight).flatMap(_ match {
        case Some(e) => Future.successful(Redirect(url, queryWithState(e.right.get, authzRequest.state), FOUND))
        case _ => f(authzRequest, client)(request)
      })
    }}
  }

  def authorize(f:(AuthzRequest, OauthClient) => Request[AnyContent] => Future[SimpleResult])(implicit ec:ExecutionContext) =
    Action.async { implicit request =>
      AuthzRequest.authorizeRequestForm.bindFromRequest.fold(onFormError, onAuthzRequest(_)(f))
    }

  def authzOk(code:String): (AuthzRequest, OauthClient) => Request[AnyContent] => Future[SimpleResult] = (authzRequest, oauthClient) => implicit request => {
    val url = oauthClient.redirectUri.orElse(authzRequest.redirectUri).get
    Future.successful(Redirect(url, queryWithState(Map(OAuth.OauthCode -> Seq(code)), authzRequest.state), FOUND))
  }

  def authzDeny: (AuthzRequest, OauthClient) => Request[AnyContent] => Future[SimpleResult] = (authzRequest, oauthClient) => implicit request => {
    val url = oauthClient.redirectUri.orElse(authzRequest.redirectUri).get
    Future.successful(Redirect(url, queryWithState(Map(OAuth.OauthError -> Seq(OAuth.ErrorCode.AccessDenied)), authzRequest.state), FOUND))
  }



}

class UUIDOauthClientFactory extends OauthClientFactory[BasicOauthClientInfo, BasicOauthClient] {
  def apply(allowedResponseType: Seq[String], info:BasicOauthClientInfo)(implicit ec: ExecutionContext): Future[BasicOauthClient] =
    Future.successful(BasicOauthClient(UUID.randomUUID().toString, UUID.randomUUID().toString, allowedResponseType, info))
}

class InMemoryOauthClientRepository[T <: OauthClient](var clients:Map[String, T] = Map[String, T]()) extends OauthClientRepository[T] {

  def find(id: String)(implicit ec: ExecutionContext): Future[Option[T]] = Future.successful(clients.get(id))

  def save(client: T)(implicit ec: ExecutionContext): Future[T] = Future.successful {
    clients += (client.id -> client)
    client
  }

  def delete(client: T)(implicit ec: ExecutionContext): Future[Unit] = Future.successful {
    clients -= client.id
  }
}

class InMemoryOauthScopeRepository[T <: OauthScope](var scopes:Map[String, T] = Map[String, T](), val defaultScopes:Option[Seq[T]] = None) extends OauthScopeRepository[T] {

  def defaults(implicit ec: ExecutionContext): Future[Option[Seq[T]]] = Future.successful(defaultScopes)

  def find(id: String)(implicit ec: ExecutionContext): Future[Option[T]] = Future.successful(scopes.get(id))

  def find(id: String*)(implicit ec: ExecutionContext): Future[Seq[(String,Option[T])]] = Future.successful(id.map(i => i -> scopes.get(i)))

  def save(scope: T)(implicit ec: ExecutionContext): Future[T] = Future.successful {
    scopes += (scope.id -> scope)
    scope
  }

  def delete(scope: T)(implicit ec: ExecutionContext): Future[Unit] = Future.successful {
    scopes -= scope.id
  }
}