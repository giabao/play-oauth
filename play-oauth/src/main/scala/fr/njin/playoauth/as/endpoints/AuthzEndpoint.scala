package fr.njin.playoauth.as.endpoints

import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future}
import play.api.data.Form
import fr.njin.playoauth.common.OAuth
import play.api.i18n.Messages
import fr.njin.playoauth.common.domain._
import fr.njin.playoauth.common
import scala.util.Either
import scala.Predef._
import scala.Some
import play.api.mvc.SimpleResult
import Results._
import play.api.http.Status._
import OauthError._
import fr.njin.playoauth.common.request.AuthzRequest
import Requests._

/**
 * User: bathily
 * Date: 17/09/13
 */
class AuthzEndpoint[I <: OauthClientInfo,T <: OauthClient, SC <: OauthScope, CO <: OauthCode[RO, P, T], RO <: OauthResourceOwner[T, P], P <: OauthPermission[T]](
  clientFactory: OauthClientFactory[I , T],
  clientRepository: OauthClientRepository[T],
  scopeRepository: OauthScopeRepository[SC],
  codeFactory: OauthCodeFactory[CO, RO, P, T],
  codeRepository: OauthCodeRepository[CO, RO, P, T]
) extends common.Logger {

  type AuthzValidation =  (AuthzRequest, T) => ExecutionContext => Future[Either[Boolean, Map[String, Seq[String]]]]

  val responseTypeCodeValidation:AuthzValidation = (authzRequest, client) => implicit ec => { Future.successful {
    Some(OAuth.ResponseType.All.contains(authzRequest.responseType)).filter(_ == true).toLeft(UnsupportedResponseTypeError())
  }}

  val scopeValidation:AuthzValidation = (authzRequest, client) => implicit ec => {
    authzRequest.scope.fold[Future[Either[Boolean, Map[String, Seq[String]]]]](Future.successful(Left(true))){ scope =>
      scopeRepository.find(scope : _*).map{ scopes =>
        val errors = scopes.filterNot(_._2.isDefined)
        Some(errors.isEmpty).filter(_ == true).toLeft(InvalidScopeError(Some(Messages(OAuth.ErrorInvalidScope, errors.map(e => e._1).mkString(" ")))))
      }
    }
  }

  val clientAuthorizedValidation:AuthzValidation = (authzRequest, client) => implicit ec => {Future.successful {
    Some(client.authorized).filter(_ == true).toLeft(AccessDeniedError())
  }}

  val clientResponseTypeValidation:AuthzValidation = (authzRequest, client) => implicit ec => { Future.successful {
    Some(client.allowedResponseType.contains(authzRequest.responseType)).filter(_ == true).toLeft(UnauthorizedClientError(Some(Messages(OAuth.ErrorUnauthorizedResponseType, authzRequest.responseType))))
  }}

  val authzValidator = Seq(responseTypeCodeValidation, scopeValidation, clientAuthorizedValidation, clientResponseTypeValidation)

  def errorToQuery(f:Form[_]):Map[String, Seq[String]] = queryWithState(InvalidRequestError(Some(f.errorsAsJson.toString())), f(OAuth.OauthState).value)

  def queryWithState(query: Map[String, Seq[String]], state:Option[String]):Map[String, Seq[String]] =
     state.map(s => query + (OAuth.OauthState -> Seq(s))).getOrElse(query)

  def register(allowedResponseType: Seq[String], allowedGrantType: Seq[String], info:I)(implicit ec:ExecutionContext): Future[T] = {
    clientFactory.apply(allowedResponseType, allowedGrantType, info).flatMap(clientRepository.save)
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

  def onAuthzRequest(authzRequest: AuthzRequest)(f:(AuthzRequest, T) => Request[AnyContent] => Future[SimpleResult])(implicit request:Request[AnyContent], ec:ExecutionContext) = {
    clientRepository.find(authzRequest.clientId).flatMap{ _.fold(Future.successful(NotFound(Messages(OAuth.ErrorClientNotFound, authzRequest.clientId)))){ client =>
      val url = client.redirectUri.orElse(authzRequest.redirectUri).get
      Future.find(authzValidator.map(_(authzRequest, client)(ec)))(_.isRight).flatMap(_ match {
        case Some(e) => Future.successful(Redirect(url, queryWithState(e.right.get, authzRequest.state), FOUND))
        case _ => f(authzRequest, client)(request)
      })
    }}
  }

  def authorize(f:(AuthzRequest, T) => Request[AnyContent] => Future[SimpleResult])(implicit ec:ExecutionContext) =
    Action.async { implicit request =>
      authorizeRequestForm.bindFromRequest.fold(onFormError, onAuthzRequest(_)(f))
    }

  def perform(owner:(Request[AnyContent]) => Option[RO])
               (onUnauthenticated:(AuthzRequest, T) => Request[AnyContent] => Future[SimpleResult],
                onUnauthorized:(AuthzRequest, T) => Request[AnyContent] => Future[SimpleResult])
               (implicit ec:ExecutionContext): (AuthzRequest, T) => Request[AnyContent] => Future[SimpleResult] = (authzRequest, oauthClient) => implicit request => {
    owner(request).fold(onUnauthenticated(authzRequest, oauthClient)(request)) { resourceOwner =>
      resourceOwner.permission(oauthClient).fold(onUnauthorized(authzRequest, oauthClient)(request)) { permission =>
        if(permission.authorized(authzRequest))
          codeFactory.apply(resourceOwner, oauthClient, authzRequest.redirectUri, authzRequest.scope)
            .flatMap(codeRepository.save)
            .flatMap { code =>
              authzAccept(code)(authzRequest, oauthClient)(request)
            }
        else
          authzDeny(authzRequest, oauthClient)(request)
      }
    }
  }

  def authzAccept(code:CO): (AuthzRequest, T) => Request[AnyContent] => Future[SimpleResult] = (authzRequest, oauthClient) => implicit request => {
    val url = oauthClient.redirectUri.orElse(authzRequest.redirectUri).get
    Future.successful(Redirect(url, queryWithState(Map(OAuth.OauthCode -> Seq(code.value)), authzRequest.state), FOUND))
  }

  def authzDeny: (AuthzRequest, T) => Request[AnyContent] => Future[SimpleResult] = (authzRequest, oauthClient) => implicit request => {
    val url = oauthClient.redirectUri.orElse(authzRequest.redirectUri).get
    Future.successful(Redirect(url, queryWithState(AccessDeniedError(), authzRequest.state), FOUND))
  }

}