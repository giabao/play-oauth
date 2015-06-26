package fr.njin.playoauth.rs

import play.api.Application
import play.api.mvc.Results._
import play.api.mvc.{Action, RequestHeader, EssentialAction}
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.iteratee.Iteratee
import fr.njin.playoauth.common.domain._
import play.api.libs.ws.{WSAuthScheme, WSRequest, WSResponse, WS}

object Oauth2Resource {

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

  def resourceOwner[TO <: OauthToken[U, C], U <: OauthResourceOwner, C <: OauthClient]
    (tokenRepository: String => Future[Option[TO]])
    (implicit token: RequestHeader => Option[String],
     ec: ExecutionContext = scala.concurrent.ExecutionContext.global): ResourceOwner[U] =

    scopes => request => {
      token(request).map(tokenRepository(_).map(
        _.filter(!_.hasExpired)
         .fold[Either[Option[U], Seq[String]]](Left(None)) { token =>
          token.scopes.map(tokenScopes => scopes.filter(tokenScopes.contains(_))).filter(_.isEmpty).toRight(Some(token.owner))
         }
      )).getOrElse(Future.successful(Left(None)))
    }

  def localResourceOwner[TO <: OauthToken[U, C], U <: OauthResourceOwner, C <: OauthClient]
    (tokenRepository: OauthTokenRepository[TO, U, C])
    (implicit token: RequestHeader => Option[String],
     ec: ExecutionContext = scala.concurrent.ExecutionContext.global): ResourceOwner[U] =

    resourceOwner[TO, U, C](tokenRepository.find)(token, ec)


  def remoteResourceOwner[TO <: OauthToken[U, C], U <: OauthResourceOwner, C <: OauthClient]
    (url: String, queryParameter: String = "value")
    (authenticate: WSRequest => WSRequest)
    (fromResponse: WSResponse => Option[TO])
    (implicit app: Application,
     token: RequestHeader => Option[String],
     ec: ExecutionContext): ResourceOwner[U] = {

    resourceOwner[TO, U, C](value => {
      authenticate(WS.url(url).withQueryString(queryParameter -> value))
        .get().map(fromResponse)
    })(token, ec)

  }

  def basicAuthRemoteResourceOwner[TO <: OauthToken[U, C], U <: OauthResourceOwner, C <: OauthClient]
    (url: String, username: String, password: String, queryParameter: String = "value")
    (fromResponse: WSResponse => Option[TO])
    (implicit app: Application,
     token: RequestHeader => Option[String],
     ec: ExecutionContext): ResourceOwner[U] = {

    remoteResourceOwner[TO, U, C](url, queryParameter) { ws =>
      ws.withAuth(username, password, WSAuthScheme.BASIC)
    }(fromResponse)
  }
}
