package fr.njin.playoauth.as.endpoints

import play.api.mvc.{Action, Controller}
import scala.concurrent.{ExecutionContext, Future}
import play.api.data.Forms._
import play.api.data.Form
import play.api.data.validation.{Invalid, Valid, Constraint}
import fr.njin.playoauth.common.OAuth
import play.api.i18n.Messages
import fr.njin.playoauth.common.client._
import play.api.data.validation.ValidationError
import java.util.UUID
import fr.njin.playoauth.common

/**
 * User: bathily
 * Date: 17/09/13
 */
class AuthzEndpoint[I <: OauthClientInfo , T <: OauthClient](factory: OauthClientFactory[I , T],
                                     repository: OauthClientRepository[T]) extends Controller with common.Logger {

  def eq(value: String): Constraint[String] = Constraint[String]("constraint.equal", value){ o:String =>
    if(value == o) Valid else Invalid(ValidationError("error.equal", o, value))
  }

  case class RegistrationRequest(redirectUri:Option[String], clientUri:Option[String], description: Option[String], name: Option[String], iconUri: Option[String])
  case class OAuthorizeRequest(responseType: String, clientId: String, redirectUri: Option[String], scope: Option[String], state: Option[String])

  val authorizeRequest = Form (
    mapping(
      OAuth.OauthResponseType -> nonEmptyText.verifying(eq("code")),
      OAuth.OauthClientId -> nonEmptyText,
      OAuth.OauthRedirectUri -> optional(text),
      OAuth.OauthScope -> optional(text),
      OAuth.OauthState -> optional(text)
    )(OAuthorizeRequest.apply)(OAuthorizeRequest.unapply)
  )

  def register(info:I)(implicit ec:ExecutionContext): Future[T] = {
    factory.create(info).flatMap(repository.save)
  }

  def deRegister(client:T)(implicit ec:ExecutionContext): Future[Unit] = {
    repository.delete(client)
  }

  def authorize(implicit ec:ExecutionContext) = Action.async { implicit request =>
    authorizeRequest.bindFromRequest.fold(f => {
      f.error(OAuth.OauthClientId).map(e => Future.successful(BadRequest(Messages(OAuth.ErrorClientMissing)))).getOrElse {
        val id = f(OAuth.OauthClientId).value.get
        repository.find(id).map(_.fold(NotFound(Messages(OAuth.ErrorClientNotFound, id))) { client =>
          client.redirectUri.orElse(f(OAuth.OauthRedirectUri).value).fold(BadRequest(Messages(OAuth.ErrorRedirectURIMissing))) { url =>
            Redirect(url)
          }
        })
      }
    }, oauthzRequest => {
      Future.successful(Ok)
    })
  }

}

object AuthzEndpointController extends AuthzEndpoint[BasicOauthClientInfo, BasicOauthClient](new UUIDOauthClientFactory(), new InMemoryOauthClientRepository[BasicOauthClient]())

class UUIDOauthClientFactory extends OauthClientFactory[BasicOauthClientInfo, BasicOauthClient] {
  def create(info:BasicOauthClientInfo)(implicit ec: ExecutionContext): Future[BasicOauthClient] = Future.successful(BasicOauthClient(UUID.randomUUID().toString, UUID.randomUUID().toString, info))
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
