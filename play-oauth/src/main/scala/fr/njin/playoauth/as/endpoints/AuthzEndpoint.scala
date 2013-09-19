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
/**
 * User: bathily
 * Date: 17/09/13
 */
class AuthzEndpoint[I <: OauthClientInfo , T <: OauthClient](factory: OauthClientFactory[I , T],
                                     repository: OauthClientRepository[T]) extends Controller with common.Logger {


  case class OAuthorizeRequest(responseType: String, clientId: String, redirectUri: Option[String], scope: Option[String], state: Option[String])

  val authorizeRequest = Form (
    mapping(
      OAuth.OauthResponseType -> nonEmptyText,
      OAuth.OauthClientId -> nonEmptyText,
      OAuth.OauthRedirectUri -> optional(text.verifying(uri)),
      OAuth.OauthScope -> optional(text),
      OAuth.OauthState -> optional(text)
    )(OAuthorizeRequest.apply)(OAuthorizeRequest.unapply)
  )

  def errorToQuery(f:Form[_]):Map[String, Seq[String]] = Map(
    OAuth.OauthError -> Seq(OAuth.ErrorCode.InvalidRequest),
    OAuth.OauthErrorDescription -> Seq(f.errorsAsJson.toString())
  ) ++ f(OAuth.OauthState).value.map(v => Map(OAuth.OauthState -> Seq(v))).getOrElse(Map())

  def register(info:I)(implicit ec:ExecutionContext): Future[T] = {
    factory.apply(info).flatMap(repository.save)
  }

  def deRegister(client:T)(implicit ec:ExecutionContext): Future[Unit] = {
    repository.delete(client)
  }

  def authorize(implicit ec:ExecutionContext) = Action.async { implicit request =>
    authorizeRequest.bindFromRequest.fold(f => {
      f.error(OAuth.OauthClientId).map(e => Future.successful(BadRequest(Messages(OAuth.ErrorClientMissing))))
        .orElse(f.error(OAuth.OauthRedirectUri).map(e => Future.successful(BadRequest(Messages(OAuth.ErrorRedirectURIInvalid, e.args)))))
        .getOrElse {
          val id = f(OAuth.OauthClientId).value.get
          repository.find(id).map(_.fold(NotFound(Messages(OAuth.ErrorClientNotFound, id))) { client =>
            client.redirectUri.orElse(f(OAuth.OauthRedirectUri).value).fold(BadRequest(Messages(OAuth.ErrorRedirectURIMissing))) { url =>
              Redirect(url, errorToQuery(f), FOUND)
            }
          })
        }
    }, oauthzRequest => {
      repository.find(oauthzRequest.clientId).map { _.fold(NotFound(Messages(OAuth.ErrorClientNotFound, oauthzRequest.clientId))){ client =>

        val url = client.redirectUri.orElse(oauthzRequest.redirectUri).get

        if(oauthzRequest.responseType != OAuth.ResponseType.Code)
          Redirect(url, Map(OAuth.OauthError -> Seq(OAuth.ErrorCode.UnsupportedResponseType)), FOUND)
        else {
          if(!client.authorized)
            Redirect(url, Map(OAuth.OauthError -> Seq(OAuth.ErrorCode.UnauthorizedClient)), FOUND)
          else {
            Ok
          }
        }
      }}
    })
  }

}

object AuthzEndpointController extends AuthzEndpoint[BasicOauthClientInfo, BasicOauthClient](new UUIDOauthClientFactory(), new InMemoryOauthClientRepository[BasicOauthClient]())

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
    clients
  }
}
