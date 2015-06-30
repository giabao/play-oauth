import java.time.Instant

import fr.njin.playoauth.common.domain.{OauthResourceOwnerRepository, BasicOauthClient, BasicOauthToken}
import fr.njin.playoauth.common.OAuth
import fr.njin.playoauth.rs.Oauth2Resource
import java.util.UUID
import com.sandinh.util.TimeUtil._
import org.specs2.specification.Scope
import play.api.mvc.{ActionBuilder, RequestHeader}
import play.api.mvc.Results._
import play.api.test.{PlaySpecification, FakeRequest, WithApplication}
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.{higherKinds, implicitConversions}

class ResourceSpec extends PlaySpecification {
  "Oauth2Resource should" ^ {

    "Accept the request" in new WithApplication() with ResourceScope {
      val r = run(ScopedAction(validScope, fakeResourceOwner))
      r.header.status must be equalTo OK
    }

    "Reject the request - Forbidden scopes" in new WithApplication() with ResourceScope {
      val r = run(ScopedAction(inValidScope, fakeResourceOwner))
      r.header.status must be equalTo FORBIDDEN
    }

    "Reject the request - Unauthorized" in new WithApplication() with ResourceScope {
      val r = run(ScopedAction(validScope, fakeEmptyResourceOwner))
      r.header.status must be equalTo UNAUTHORIZED
    }
  }

  "Local Oauth2Resource should" ^ {

    "Accept the request" in new WithApplication() with ResourceScope {
      val r = run(ScopedAction(validScope, tokenFound))
      r.header.status must be equalTo OK
    }

    "Reject the request - Forbidden scopes" in new WithApplication() with ResourceScope {
      val r = run(ScopedAction(inValidScope, tokenFound))
      r.header.status must be equalTo FORBIDDEN
    }

    "Reject the request - Unauthorized" in new WithApplication() with ResourceScope {
      val r = run(ScopedAction(validScope, tokenNotFound))
      r.header.status must be equalTo UNAUTHORIZED
    }

    "Reject the request - Token expired" in new WithApplication() with ResourceScope {
      val r = run(ScopedAction(validScope, tokenExpired))
      r.header.status must be equalTo UNAUTHORIZED
    }
  }

  def run[R[_]](action: ActionBuilder[R]) = await(call(action(Ok), FakeRequest()))
}

trait ResourceScope extends Scope with Oauth2Resource[BasicOauthToken, User] {
  implicit def tokenToUserInfo(token: RequestHeader => Option[String]): UserInfo =
    toUserInfo(token, tokenRepo.find, userRepo.find)

  val validScope = Seq("Valid scope")
  val inValidScope = Seq("Invalid scope")

  val userRepo = new OauthResourceOwnerRepository[User] {
    def find(id: String): Future[Option[User]] = Future.successful(if (id == user.id) Some(user) else None)
  }

  val user = User("username", "password", Map.empty)
  val client = new BasicOauthClient("id", "secret", OAuth.ResponseType.All, OAuth.GrantType.All)
  val token = new BasicOauthToken("token", user.id, client.id, "Bearer", scopes = Some(validScope))

  val tokenFound: RequestHeader => Option[String] = request => Some("token")
  val tokenNotFound: RequestHeader => Option[String] = request => None
  val tokenExpired: RequestHeader => Option[String] = request => Some("expiredToken")

  val expiredToken = new BasicOauthToken("expiredToken", user.id, client.id, "Bearer", issueAt = Instant.now - 1.hour)

  val fakeResourceOwner: Seq[String] => RequestHeader => Future[Either[Option[User], Seq[String]]] = scopes => request => {
    Future.successful {
      scopes match {
        case `validScope` => Left(Some(user))
        case _ => Right(scopes)
      }
    }
  }

  val fakeEmptyResourceOwner: Seq[String] => RequestHeader => Future[Either[Option[User], Seq[String]]] = scopes => request =>
    Future.successful(Left(None))

  lazy val tokenRepo = new InMemoryOauthTokenRepository[BasicOauthToken](Set(token, expiredToken)){
    def apply(ownerId: String, clientId: String, redirectUri: Option[String], scopes: Option[Seq[String]]): Future[BasicOauthToken] = Future.successful{
      val token = new BasicOauthToken(UUID.randomUUID().toString, ownerId, clientId, "example")
      tokens = tokens + token
      token
    }
  }
}
