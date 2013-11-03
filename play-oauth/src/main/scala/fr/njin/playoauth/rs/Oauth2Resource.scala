package fr.njin.playoauth.rs

import play.api.mvc.Results._
import play.api.mvc.{Action, RequestHeader, EssentialAction}
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.iteratee.Iteratee
import fr.njin.playoauth.common.domain._
import scala.Some
import play.api.libs.ws.{Response, WS}
import com.ning.http.client.Realm.AuthScheme

object Oauth2Resource {

  def scoped[U](scopes: String*)(action: U => EssentialAction)
               (onUnauthorized: EssentialAction = Action { Unauthorized("") },
                onForbidden: Seq[String] => EssentialAction = scopes => Action { Forbidden("") })
               (implicit resourceOwner: Seq[String] => RequestHeader => Future[Either[Option[U], Seq[String]]],
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

  def resourceOwner[TO <: OauthToken[U, P, C], U <: OauthResourceOwner, P <: OauthPermission[C], C <: OauthClient]
    (tokenRepository: String => Future[Option[TO]])
    (implicit token: RequestHeader => Option[String],
     ec: ExecutionContext = scala.concurrent.ExecutionContext.global): Seq[String] => RequestHeader => Future[Either[Option[U], Seq[String]]] =

    scopes => request => {
      token(request).map(tokenRepository(_).map(_.fold[Either[Option[U], Seq[String]]](Left(None)){token =>
        token.scope.map(tokenScopes => scopes.filter(tokenScopes.contains(_))).filter(_.isEmpty).toRight(Some(token.owner))
      })).getOrElse(Future.successful(Left(None)))
    }

  def localResourceOwner[TO <: OauthToken[U, P, C], U <: OauthResourceOwner, P <: OauthPermission[C], C <: OauthClient]
    (tokenRepository: OauthTokenRepository[TO, U, P, C])
    (implicit token: RequestHeader => Option[String],
     ec: ExecutionContext = scala.concurrent.ExecutionContext.global): Seq[String] => RequestHeader => Future[Either[Option[U], Seq[String]]] =

    resourceOwner[TO, U, P, C](tokenRepository.find)(token, ec)


  def remoteResourceOwner[TO <: OauthToken[U, P, C], U <: OauthResourceOwner, P <: OauthPermission[C], C <: OauthClient]
    (url: String, queryParameter: String = "value")
    (authenticate: WS.WSRequestHolder => WS.WSRequestHolder)
    (fromResponse: Response => Option[TO])
    (implicit token: RequestHeader => Option[String],
     ec: ExecutionContext = scala.concurrent.ExecutionContext.global): Seq[String] => RequestHeader => Future[Either[Option[U], Seq[String]]] = {

    resourceOwner[TO, U, P, C](value => {
      authenticate(WS.url(url).withQueryString(queryParameter -> value))
        .get().map(fromResponse)
    })(token, ec)

  }

  def remoteResourceOwner[TO <: OauthToken[U, P, C], U <: OauthResourceOwner, P <: OauthPermission[C], C <: OauthClient]
    (url: String, username: String, password: String, queryParameter: String = "value")
    (fromResponse: Response => Option[TO])
    (implicit token: RequestHeader => Option[String],
     ec: ExecutionContext = scala.concurrent.ExecutionContext.global): Seq[String] => RequestHeader => Future[Either[Option[U], Seq[String]]] = {

    remoteResourceOwner[TO, U, P, C](url, queryParameter) { ws =>
      ws.withAuth(username, password, AuthScheme.BASIC)
    }(fromResponse)(token, ec)

  }

}
