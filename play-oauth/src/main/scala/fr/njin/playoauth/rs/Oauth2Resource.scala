package fr.njin.playoauth.rs

import play.api.mvc.Results._
import play.api.mvc.Security.AuthenticatedRequest
import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future}
import fr.njin.playoauth.common.domain._
import scala.language.{implicitConversions, reflectiveCalls}

trait Oauth2Resource[TO <: OauthToken, U <: OauthResourceOwner] {
  /** provided scopes => RequestHeader => Future[Either[Option[User], omit scopes]] */
  type UserInfo = Seq[String] => RequestHeader => Future[Either[Option[U], Seq[String]]]

  /** using example:
    * {{{
    *   def tokenRepo: OauthTokenRepository[TO] = ???
    *
    *   def userRepo: OauthResourceOwnerRepository[U] = ???
    *
    *   val userInfo = toUserInfo(Utils.parseBearer, tokenRepo.find, userRepo.find)
    *
    *   def someResourceAction = ScopedAction(Seq("some_scope"), userInfo){ request =>
    *     Ok("Hello " + request.user)
    *   }}
    * }}}
    *
    * example with token fetch from remote:
    * {{{
    *   def userRepo: OauthResourceOwnerRepository[U] = ???
    *
    *   //wsApi: WSAPI is @Injected
    *   def tokenFetcher(value: String) =
    *     wsApi.url("http://localhost:9000/oauth2/token")
    *       .withAuth("CLIENT_ID","CLIENT_SECRET", WSAuthScheme.BASIC)
    *       .withQueryString("value" -> value)
    *       .get().map { response =>
    *         response.status match {
    *           case Status.OK => Json.fromJson[AuthToken](response.json).asOpt
    *           case _ => None
    *       }
    *
    *   def userInfo = toUserInfo(Utils.parseBearer, tokenFetcher, userRepo.find)
    *
    *   def someResourceAction = ScopedAction(Seq("some_scope"), userInfo){ request =>
    *     Ok("Hello " + request.user)
    *   }}
    * }}}
    */
  class ScopedAction(scopes: Seq[String],
                     userInfo: UserInfo,
                     onUnauthorized: RequestHeader => Result,
                     onForbidden: Seq[String] => RequestHeader => Result)
    extends ActionBuilder[({type R[A] = AuthenticatedRequest[A, U]})#R]
  {
    def invokeBlock[A](req: Request[A], block: (AuthenticatedRequest[A, U]) => Future[Result]) =
      userInfo(scopes)(req).flatMap { either =>
        either.fold(
          ownerOpt => ownerOpt.fold(Future successful onUnauthorized(req)) { owner =>
            block(new AuthenticatedRequest(owner, req))
          },
          Future successful onForbidden(_)(req)
        )
      }(this.executionContext)
  }

  object ScopedAction {
    def apply(scopes: Seq[String],
              userInfo: UserInfo,
              onUnauthorized: RequestHeader => Result = _ => Unauthorized,
              onForbidden: Seq[String] => RequestHeader => Result = scopes => _ => Forbidden) =
      new ScopedAction(scopes, userInfo, onUnauthorized, onForbidden)
  }

  def toUserInfo(tokenValue: RequestHeader => Option[String],
                 token: String => Future[Option[TO]],
                 user: String => Future[Option[U]])
                (implicit ec: ExecutionContext): UserInfo =
    scopes => request =>
      tokenValue(request).map(token(_).flatMap(
        _.filter(!_.hasExpired) match {
          case None => Future successful Left(None)
          case Some(tk) =>
            tk.scopes
              .map(tokenScopes => scopes.filter(tokenScopes.contains))
              .filter(_.isEmpty) match {
              case None => user(tk.ownerId).map(Left(_))
              case Some(omitScopes) => Future successful Right(omitScopes)
            }
        }
      )).getOrElse(Future successful Left(None))
}
