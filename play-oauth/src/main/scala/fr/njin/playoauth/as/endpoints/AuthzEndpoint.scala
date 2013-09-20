package fr.njin.playoauth.as.endpoints

import play.api.mvc.{Action, Controller}
import scala.concurrent.{ExecutionContext, Future}
import play.api.data.Forms._
import play.api.data.Form
import fr.njin.playoauth.common.OAuth
import play.api.i18n.Messages
import fr.njin.playoauth.common.client._
import java.util.UUID
import fr.njin.playoauth.common
import Constraints._
import scala.util.Either
import scala.Predef._

/**
 * User: bathily
 * Date: 17/09/13
 */
class AuthzEndpoint[I <: OauthClientInfo,T <: OauthClient, SC <: OauthScope](
  clientFactory: OauthClientFactory[I , T],
  clientRepository: OauthClientRepository[T],
  scopeRepository: OauthScopeRepository[SC]
) extends Controller with common.Logger {


  type AuthzValidation =  (AuthzRequest, OauthClient) => ExecutionContext => Future[Either[Boolean, Map[String, Seq[String]]]]

  val responseTypeCodeValidation:AuthzValidation = (authzRequest, client) => implicit ec => { Future.successful {
    Some(authzRequest.responseType == OAuth.ResponseType.Code).filter(_ == true).toLeft(Map(OAuth.OauthError -> Seq(OAuth.ErrorCode.UnsupportedResponseType)))
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
    Some(client.authorized).filter(_ == true).toLeft(Map(OAuth.OauthError -> Seq(OAuth.ErrorCode.UnauthorizedClient)))
  }}

  val authzValidator = Seq(responseTypeCodeValidation, scopeValidation, clientAuthorizedValidation)


  def errorToQuery(f:Form[_]):Map[String, Seq[String]] = errorQuery(Map(
    OAuth.OauthError -> Seq(OAuth.ErrorCode.InvalidRequest),
    OAuth.OauthErrorDescription -> Seq(f.errorsAsJson.toString())
  ), f(OAuth.OauthState).value)

  def errorQuery(query: Map[String, Seq[String]], state:Option[String]):Map[String, Seq[String]] =
     state.map(s => query + (OAuth.OauthState -> Seq(s))).getOrElse(query)

  def register(info:I)(implicit ec:ExecutionContext): Future[T] = {
    clientFactory.apply(info).flatMap(clientRepository.save)
  }

  def deRegister(client:T)(implicit ec:ExecutionContext): Future[Unit] = {
    clientRepository.delete(client)
  }

  def authorize(implicit ec:ExecutionContext) = Action.async { implicit request =>
    AuthzRequest.authorizeRequestForm.bindFromRequest.fold(f => {
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
    }, oauthzRequest => {
      clientRepository.find(oauthzRequest.clientId).flatMap{ _.fold(Future.successful(NotFound(Messages(OAuth.ErrorClientNotFound, oauthzRequest.clientId)))){ client =>

        val url = client.redirectUri.orElse(oauthzRequest.redirectUri).get

        Future.find(authzValidator.map(_(oauthzRequest, client)(ec)))(_.isRight).map(_ match {
          case Some(e) => Redirect(url, errorQuery(e.right.get, oauthzRequest.state), FOUND)
          case _ => Ok
        })

      }}
    })
  }

}

object AuthzEndpointController extends AuthzEndpoint[BasicOauthClientInfo, BasicOauthClient, BasicOauthScope](
  new UUIDOauthClientFactory(),
  new InMemoryOauthClientRepository[BasicOauthClient](),
  new InMemoryOauthScopeRepository[BasicOauthScope]()
)

class UUIDOauthClientFactory extends OauthClientFactory[BasicOauthClientInfo, BasicOauthClient] {
  def apply(info:BasicOauthClientInfo)(implicit ec: ExecutionContext): Future[BasicOauthClient] = Future.successful(BasicOauthClient(UUID.randomUUID().toString, UUID.randomUUID().toString, info))
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