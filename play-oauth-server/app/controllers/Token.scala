package controllers

import play.api.mvc._
import scalikejdbc.async.AsyncDBSession
import scala.concurrent.{Future, ExecutionContext}
import fr.njin.playoauth.as.endpoints.{SecretKeyClientAuthentication, TokenEndpoint}
import models._
import domain.DB._
import models.AuthToken
import fr.njin.playoauth.as.OauthError
import fr.njin.playoauth.common.OAuth
import play.api.i18n.Messages
import domain.oauth2._
import play.api.mvc.AnyContentAsFormUrlEncoded
import scala.Some
import fr.njin.playoauth.Utils
import play.api.libs.json.Json

object Token extends Controller {

  /**
   * Token endpoint call
   *
   * @return see [[fr.njin.playoauth.as.endpoints.Token.token]]
   */
  def token = InTx { implicit tx =>
    Action.async(parse.urlFormEncoded.map(new AnyContentAsFormUrlEncoded(_))) { request =>
      new TokenEndpointController().token(
        (u,p) => User.findByEmail(u).map(_.filter(_.passwordMatch(p))),
        app => Future.successful(app.owner)
      ).apply(request)
    }
  }

  /**
   * Token info
   *
   * Send a token as json to the client.
   * Use http basic authentication to authenticate the client.
   *
   * @param value value of the token
   * @return see [[fr.njin.playoauth.as.endpoints.Token.info]]
   */
  def info(value: String) = InTx { implicit tx =>
    Action.async { request =>
      new TokenEndpointController().info(value){ request =>
          Utils.parseBasicAuth(request).map{
            case (id, secret) => App.find(id).map(_.filter(_.secret == secret))
          }.getOrElse(Future.successful(None))
      } { token =>
        Future.successful(Ok(Json.toJson(token)))
      }().apply(request)
    }
  }

}

/**
 * We need a custom token endpoint because
 * we need to pass the database session and the
 * database execution context to all our
 * repositories and factories.

 * @param session the database session
 * @param ec the database execution context
 */
class TokenEndpointController(implicit val session:AsyncDBSession, ec: ExecutionContext)
  extends TokenEndpoint[App, AuthCode, User, Permission, AuthToken](
    new AppRepository(),
    new AuthCodeRepository(),
    new AuthTokenFactory(),
    new AuthTokenRepository()
  ) with SecretKeyClientAuthentication[App] {


  def authenticate(id: String, secret: String): Future[Either[Option[App], OauthError]] =
    clientRepository.find(id).map(_.fold[Either[Option[App], OauthError]](Left(None)) { app =>
      if(app.secret == secret)
        Left(Some(app))
      else
        Right(OauthError.invalidClientError(Some(Messages(OAuth.ErrorClientCredentialsDontMatch))))
    })

}
