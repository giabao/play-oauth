package controllers

import play.api.mvc._
import scalikejdbc.async.AsyncDBSession
import scala.concurrent.{Future, ExecutionContext}
import fr.njin.playoauth.as.endpoints.{SecretKeyClientAuthentication, ClientAuthentication, TokenEndpoint}
import fr.njin.playoauth.common.domain.{OauthClientRepository, BasicOauthScope}
import models._
import domain.DB._
import models.AuthToken
import fr.njin.playoauth.as.OauthError
import fr.njin.playoauth.common.OAuth
import domain._
import play.api.i18n.Messages

object Token extends Controller {

  def token = InTx { implicit tx =>
    Action.async(parse.urlFormEncoded.map(new AnyContentAsFormUrlEncoded(_))) { request =>
      val endpoint = new TokenEndpointController()
      endpoint.token(endpoint.perform(
        (u,p) => User.findByEmail(u).map(_.filter(_.passwordMatch(p))),
        app => Future.successful(app.owner)
      )).apply(request)
    }
  }

}

trait AppAuthentication extends SecretKeyClientAuthentication[App] {

  def repository: OauthClientRepository[App]

  def authenticate(id: String, secret: String): Future[Either[Option[App], OauthError]] =
    repository.find(id).map(_.fold[Either[Option[App], OauthError]](Left(None)) { app =>
      if(app.secret == secret)
        Left(Some(app))
      else
        Right(OauthError.InvalidClientError(Some(Messages(OAuth.ErrorClientCredentialsDontMatch))))
    })
}

class TokenEndpointController(implicit val session:AsyncDBSession, ec: ExecutionContext)
  extends TokenEndpoint[App, BasicOauthScope, AuthCode, User, Permission, AuthToken](
    new AppRepository(),
    new InMemoryOauthScopeRepository[BasicOauthScope](Map("basic" -> new BasicOauthScope("basic"))),
    new AuthCodeFactory(),
    new AuthCodeRepository(),
    new AuthTokenFactory(),
    new AuthTokenRepository()
  ) with AppAuthentication {

  def repository: OauthClientRepository[App] = new AppRepository()
}
