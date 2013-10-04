package domain

import scala.concurrent.{ExecutionContext, Future}
import play.api.mvc._
import play.api.mvc.SimpleResult

/**
 * User: bathily
 * Date: 03/10/13
 */
object Security {

  case class AuthenticatedRequest[A, U](user: U, request: Request[A]) extends WrappedRequest[A](request)

  class AuthenticatedActionBuilder[U](userInfo: RequestHeader => Future[Option[U]],
                                      onUnauthorized: RequestHeader => Future[SimpleResult])
                                     (implicit ec: ExecutionContext) extends ActionBuilder[({ type R[A] = AuthenticatedRequest[A, U] })#R] {

    protected def invokeBlock[A](request: Request[A], block: (AuthenticatedRequest[A, U]) => Future[SimpleResult]): Future[SimpleResult] = {
      userInfo(request).flatMap(_.fold(onUnauthorized(request))(u => block(AuthenticatedRequest(u, request))))
    }

  }

  object AuthenticatedActionBuilder {
    def apply[U](userInfo: RequestHeader => Future[Option[U]], onUnauthorized: RequestHeader => Future[SimpleResult])
                (implicit ec: ExecutionContext) =
      new AuthenticatedActionBuilder[U](userInfo, onUnauthorized)
  }


}
