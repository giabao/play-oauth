import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.mvc.{RequestHeader, Action, EssentialAction}
import play.api.mvc.Results._
import scala.concurrent.Future
import scala.Predef._
import scala.Some

class ResourceSpec extends Specification {


  "Oauth2Resource should" ^ {

    "Accept the request" in new ResourceScope {
      //val r = Helpers.await(Oauth2Resource.scoped[User]("Valid")(action)()(fakeResourceOwner).apply(FakeRequest()).run)
      //r.header.status must be equalTo OK

      /*
      val a = EssentialAction { request =>
          Action { Ok("") }(request)
        }

      Helpers.await(a(FakeRequest()).run).header.status must be equalTo OK
      */
    }

    "Reject the request - Unauthorized" in new ResourceScope {
      /*
      val r = Helpers.await(Oauth2Resource.scoped[User]("Valid")(action)()(fakeEmptyResourceOwner).apply(FakeRequest()).run)
      println(r.header.status)
      r.header.status must be equalTo UNAUTHORIZED
      */
    }
  }

}

trait ResourceScope extends Scope {

  val user = User("username", "password", Map.empty)
  val action: User => EssentialAction = u => Action { Ok("az") }

  val fakeResourceOwner: Seq[String] => RequestHeader => Future[Either[Option[User], Seq[String]]] = scopes => request => {
    Future.successful {
      scopes match {
        case Seq("Valid") => Left(Some(user))
        case _ => Right(scopes)
      }
    }
  }

  val fakeEmptyResourceOwner: Seq[String] => RequestHeader => Future[Either[Option[User], Seq[String]]] = scopes => request =>
    Future.successful(Left(None))
}
