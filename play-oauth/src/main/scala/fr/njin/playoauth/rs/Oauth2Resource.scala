package fr.njin.playoauth.rs

import play.api.Application
import play.api.mvc.Results._
import play.api.mvc.{Action, RequestHeader, EssentialAction}
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.iteratee.Iteratee
import fr.njin.playoauth.common.domain._
import play.api.libs.ws.{WSAuthScheme, WSRequest, WSResponse, WS}

object Oauth2Resource {

  //scopes => RequestHeader => Future[Either[Option[User], scopes?]]
  //U is ResourceOwner
  type ResourceOwner[U] = Seq[String] => RequestHeader => Future[Either[Option[U], Seq[String]]]

  def scoped[U](scopes: String*)(action: U => EssentialAction)
               (onUnauthorized: EssentialAction = Action { Unauthorized("") },
                onForbidden: Seq[String] => EssentialAction = scopes => Action { Forbidden("") })
               (implicit resourceOwner: ResourceOwner[U],
                ec: ExecutionContext = scala.concurrent.ExecutionContext.global): EssentialAction =

    EssentialAction { request => Iteratee.flatten(
      resourceOwner(scopes)(request).map {
        _.fold(
          _.fold(onUnauthorized(request)) { owner =>
            action(owner)(request)
          },
          onForbidden(_)(request)
        )
      }
    )}

  def resourceOwner[TO <: OauthToken, U <: OauthResourceOwner]
    ( tokenRepository: String => Future[Option[TO]],
      userRepository: String => Future[Option[U]])
    (implicit token: RequestHeader => Option[String],
     ec: ExecutionContext = scala.concurrent.ExecutionContext.global): ResourceOwner[U] =

    scopes => request => {
      token(request).map(tokenRepository(_).flatMap(
        _.filter(!_.hasExpired) match {
            case None => Future successful Left(None)
            case Some(tk) =>
              tk.scopes
                .map(tokenScopes => scopes.filter(tokenScopes.contains))
                .filter(_.isEmpty) match {
                  case None => userRepository(tk.ownerId).map(Left(_))
                  case Some(diffScopes) => Future successful Right(diffScopes)
                }
         }
      )).getOrElse(Future successful Left(None))
    }

  def localResourceOwner[TO <: OauthToken, U <: OauthResourceOwner]
    (tokenRepository: OauthTokenRepository[TO],
     userRepository: OauthResourceOwnerRepository[U])
    (implicit token: RequestHeader => Option[String],
     ec: ExecutionContext = scala.concurrent.ExecutionContext.global): ResourceOwner[U] =

    resourceOwner[TO, U](tokenRepository.find, userRepository.find)(token, ec)


  def remoteResourceOwner[TO <: OauthToken, U <: OauthResourceOwner]
    (url: String, queryParameter: String = "value")
    (authenticate: WSRequest => WSRequest)
    (fromResponse: WSResponse => Option[TO])
    (userRepository: String => Future[Option[U]])
    (implicit app: Application,
     token: RequestHeader => Option[String],
     ec: ExecutionContext): ResourceOwner[U] = {

    def tokenRepository(value: String) =
      authenticate(WS.url(url).withQueryString(queryParameter -> value))
      .get().map(fromResponse)

    resourceOwner[TO, U](tokenRepository, userRepository)(token, ec)
  }

  def basicAuthRemoteResourceOwner[TO <: OauthToken, U <: OauthResourceOwner]
    (url: String, username: String, password: String, queryParameter: String = "value")
    (fromResponse: WSResponse => Option[TO])
    (userRepository: String => Future[Option[U]])
    (implicit app: Application,
     token: RequestHeader => Option[String],
     ec: ExecutionContext): ResourceOwner[U] = {

    remoteResourceOwner[TO, U](url, queryParameter) { ws =>
      ws.withAuth(username, password, WSAuthScheme.BASIC)
    }(fromResponse)(userRepository)
  }
}
