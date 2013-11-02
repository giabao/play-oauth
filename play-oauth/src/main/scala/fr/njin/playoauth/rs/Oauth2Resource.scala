package fr.njin.playoauth.rs

import play.api.mvc.Results._
import play.api.mvc.{Action, RequestHeader, EssentialAction}
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.iteratee.Iteratee
import fr.njin.playoauth.common.domain._

object Oauth2Resource {

  def scoped[U](scopes: String*)(action: U => EssentialAction)
               (onUnauthorized: EssentialAction = onUnauthorized,
                onForbidden: Seq[String] => EssentialAction = onForbidden)
               (implicit resourceOwner: Seq[String] => RequestHeader => Future[Either[Option[U], Seq[String]]], ec: ExecutionContext = scala.concurrent.ExecutionContext.global): EssentialAction =
    
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

  def onUnauthorized: EssentialAction = Action { Unauthorized("") }
  def onForbidden: Seq[String] => EssentialAction = rejectedScopes => Action { Forbidden("") }

  def resourceOwner[TO <: OauthToken[U, P, C], U <: OauthResourceOwner, P <: OauthPermission[C], C <: OauthClient]
    (tokenRepository: String => Future[Option[TO]])
    (implicit token: RequestHeader => Option[String], ec: ExecutionContext = scala.concurrent.ExecutionContext.global): Seq[String] => RequestHeader => Future[Either[Option[U], Seq[String]]] =

    scopes => request => {
      token(request).map(tokenRepository(_).map(_.fold[Either[Option[U], Seq[String]]](Left(None)){token =>
        token.scope.map(tokenScopes => scopes.filter(tokenScopes.contains(_))).filter(_.isEmpty).toRight(Some(token.owner))
      })).getOrElse(Future.successful(Left(None)))
    }

  def localResourceOwner[TO <: OauthToken[U, P, C], U <: OauthResourceOwner, P <: OauthPermission[C], C <: OauthClient]
    (tokenRepository: OauthTokenRepository[TO, U, P, C])
    (implicit token: RequestHeader => Option[String], ec: ExecutionContext = scala.concurrent.ExecutionContext.global): Seq[String] => RequestHeader => Future[Either[Option[U], Seq[String]]] =

    resourceOwner[TO, U, P, C](tokenRepository.find)(token, ec)
}
