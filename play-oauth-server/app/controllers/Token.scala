package controllers

import play.api.mvc._
import scalikejdbc.async.AsyncDBSession
import scala.concurrent.{Future, ExecutionContext}
import fr.njin.playoauth.as.endpoints.{SecretKeyClientAuthentication, TokenEndpoint}
import fr.njin.playoauth.common.domain.BasicOauthScope
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

  def token = InTx { implicit tx =>
    Action.async(parse.urlFormEncoded.map(new AnyContentAsFormUrlEncoded(_))) { request =>
      new TokenEndpointController().token(
        (u,p) => User.findByEmail(u).map(_.filter(_.passwordMatch(p))),
        app => Future.successful(app.owner)
      ).apply(request)
    }
  }

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

class TokenEndpointController(implicit val session:AsyncDBSession, ec: ExecutionContext)
  extends TokenEndpoint[App, BasicOauthScope, AuthCode, User, Permission, AuthToken](
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
        Right(OauthError.InvalidClientError(Some(Messages(OAuth.ErrorClientCredentialsDontMatch))))
    })

}
