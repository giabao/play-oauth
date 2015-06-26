package domain

import scala.concurrent.{ExecutionContext, Future}
import play.api.mvc._
import models.User
import java.util.UUID
import play.api.libs.Crypto
import controllers.routes
import scalikejdbc.async.AsyncDBSession
import play.api.libs.iteratee.{Input, Done, Iteratee}

/**
 * User: bathily
 * Date: 03/10/13
 */
object Security {

  case class AuthenticatedRequest[A, U](user: U, request: Request[A]) extends WrappedRequest[A](request)

  class AuthenticatedActionBuilder[U](userInfo: RequestHeader => Future[Option[U]],
                                      onUnauthorized: RequestHeader => Future[Result])
                                     (implicit ec: ExecutionContext) extends ActionBuilder[({ type R[A] = AuthenticatedRequest[A, U] })#R] {

    protected def invokeBlock[A](request: Request[A], block: (AuthenticatedRequest[A, U]) => Future[Result]): Future[Result] = {
      userInfo(request).flatMap(_.fold(onUnauthorized(request))(u => block(AuthenticatedRequest(u, request))))
    }

  }

  object AuthenticatedActionBuilder {
    def apply[U](userInfo: RequestHeader => Future[Option[U]],
                 onUnauthorized: RequestHeader => Future[Result])
                (implicit ec: ExecutionContext) =
      new AuthenticatedActionBuilder[U](userInfo, onUnauthorized)
  }

  def Authenticated[U](userInfo: RequestHeader => Future[Option[U]],
                       onUnauthorized: EssentialAction)
                      (action: U => EssentialAction)(implicit ec: ExecutionContext): EssentialAction = {
    EssentialAction { request =>
      Iteratee.flatten(userInfo(request).map(_.fold(onUnauthorized(request))(u => action(u)(request))))
    }
  }

  def UserInfo: (AsyncDBSession, ExecutionContext) => RequestHeader => Future[Option[User]] = (session, ec) => request => {
    (for {
      sessionId <- request.session.get("sessionId")
      email <- request.session.get("email")
      password <- request.session.get("password")
    } yield (sessionId, email, password)).map(credentials =>
        User
          .findByEmail(Crypto.decryptAES(credentials._2))(session, ec)
          .map(_.filter(u => u.password.equals(Crypto.decryptAES(credentials._3))))(ec)
    ).getOrElse(Future.successful(None))
  }

  def OnUnauthorized: RequestHeader => Future[Result] = request =>
    Future.successful(Results.Redirect(routes.Application.signIn(Option(request.uri)).url))

  trait AuthenticationHandler {

    def logIn(user:User, redirectUrl: String): Future[Result] = {
      Future.successful(Results.Redirect(redirectUrl).withNewSession
        .withSession(
          "sessionId" -> UUID.randomUUID().toString,
          "email" -> Crypto.encryptAES(user.email),
          "password" -> Crypto.encryptAES(user.password)
        )
      )
    }

    def logOut(user:User): Future[Result] = {
      Future.successful(Results.Redirect(routes.Application.signIn()).withNewSession)
    }
  }

  class UserAuthenticatedActionBuilder(implicit session: AsyncDBSession, ec: ExecutionContext)
    extends AuthenticatedActionBuilder[User](
      UserInfo(session, ec),
      OnUnauthorized
    ) with AuthenticationHandler


  def AuthenticatedAction(implicit session: AsyncDBSession, ec: ExecutionContext) = new UserAuthenticatedActionBuilder

  def WithUser(implicit session: AsyncDBSession, ec: ExecutionContext): (User => EssentialAction) => EssentialAction = {
    Authenticated(UserInfo(session, ec), EssentialAction { request =>
      Iteratee.flatten(OnUnauthorized(request).map(Done(_, Input.Empty)))
    })
  }

}
