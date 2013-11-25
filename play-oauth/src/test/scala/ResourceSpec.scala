import fr.njin.playoauth.common.domain.{BasicOauthClient, BasicOauthToken}
import fr.njin.playoauth.common.OAuth
import fr.njin.playoauth.rs.Oauth2Resource._
import java.util.UUID
import org.joda.time.DateTime
import org.specs2.specification.Scope
import play.api.mvc.{RequestHeader, Action, EssentialAction}
import play.api.mvc.Results._
import play.api.test.{PlaySpecification, FakeRequest, WithApplication}
import scala.concurrent.duration.DurationLong
import scala.concurrent.Future

class ResourceSpec extends PlaySpecification {


  "Oauth2Resource should" ^ {

    "Accept the request" in new WithApplication() with ResourceScope {
      val r = await(scoped[User](validScope)(action)()(fakeResourceOwner).apply(FakeRequest()).run)
      r.header.status must be equalTo OK
    }

    "Reject the request - Forbidden scopes" in new WithApplication() with ResourceScope {
      val r = await(scoped[User](inValidScope)(action)()(fakeResourceOwner).apply(FakeRequest()).run)
      r.header.status must be equalTo FORBIDDEN
    }

    "Reject the request - Unauthorized" in new WithApplication() with ResourceScope {
      val r = await(scoped[User](validScope)(action)()(fakeEmptyResourceOwner).apply(FakeRequest()).run)
      r.header.status must be equalTo UNAUTHORIZED
    }
  }

  "Local Oauth2Resource should" ^ {

    "Accept the request" in new WithApplication() with ResourceScope {
      val r = await(scoped[User](validScope)(action)()(localResourceOwner(tokenRepository)(tokenFound)).apply(FakeRequest()).run)
      r.header.status must be equalTo OK
    }

    "Reject the request - Forbidden scopes" in new WithApplication() with ResourceScope {
      val r = await(scoped[User](inValidScope)(action)()(localResourceOwner(tokenRepository)(tokenFound)).apply(FakeRequest()).run)
      r.header.status must be equalTo FORBIDDEN
    }

    "Reject the request - Unauthorized" in new WithApplication() with ResourceScope {
      val r = await(scoped[User](validScope)(action)()(localResourceOwner(tokenRepository)(tokenNotFound)).apply(FakeRequest()).run)
      r.header.status must be equalTo UNAUTHORIZED
    }

    "Reject the request - Token expired" in new WithApplication() with ResourceScope {
      val r = await(scoped[User](validScope)(action)()(localResourceOwner(tokenRepository)(tokenExpired)).apply(FakeRequest()).run)
      r.header.status must be equalTo UNAUTHORIZED
    }
  }

}

trait ResourceScope extends Scope {

  val validScope = "Valid scope"
  val inValidScope = "Invalid scope"

  val user = User("username", "password", Map.empty)
  val client = new BasicOauthClient("id", "secret", OAuth.ResponseType.All, OAuth.GrantType.All)
  val token = new BasicOauthToken[User, BasicOauthClient](user, client, "token", "Bearer", scopes = Some(Seq(validScope)))
  val action: User => EssentialAction = u => Action { Ok("") }

  val tokenFound: RequestHeader => Option[String] = request => Some("token")
  val tokenNotFound: RequestHeader => Option[String] = request => None
  val tokenExpired: RequestHeader => Option[String] = request => Some("expiredToken")

  val expiredToken = new BasicOauthToken[User, BasicOauthClient](user, client, "expiredToken", "Bearer",
    issueAt = DateTime.now().minusHours(1).getMillis, expiresIn = Some((10 minutes).toMillis))

  val fakeResourceOwner: Seq[String] => RequestHeader => Future[Either[Option[User], Seq[String]]] = scopes => request => {
    Future.successful {
      scopes match {
        case Seq(`validScope`) => Left(Some(user))
        case _ => Right(scopes)
      }
    }
  }

  val fakeEmptyResourceOwner: Seq[String] => RequestHeader => Future[Either[Option[User], Seq[String]]] = scopes => request =>
    Future.successful(Left(None))

  lazy val tokenRepository = new InMemoryOauthTokenRepository[BasicOauthToken[User, BasicOauthClient], User, BasicOauthClient](Set(token, expiredToken)){
    def apply(owner: User, client: BasicOauthClient, redirectUri: Option[String], scopes: Option[Seq[String]]): Future[BasicOauthToken[User, BasicOauthClient]] = Future.successful{
      val token:BasicOauthToken[User, BasicOauthClient] = new BasicOauthToken(owner, client, UUID.randomUUID().toString, "example")
      tokens = tokens + token
      token
    }
  }

}
