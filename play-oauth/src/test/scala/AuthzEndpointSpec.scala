import fr.njin.playoauth.as.endpoints
import fr.njin.playoauth.as.endpoints.{InMemoryOauthClientRepository, UUIDOauthClientFactory}
import fr.njin.playoauth.common.client.{BasicOauthClient, BasicOauthClientInfo}
import fr.njin.playoauth.common.OAuth
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.time.NoTimeConversions
import play.api.test._
import play.api.test.Helpers._
import scala.concurrent.{ExecutionContext, Await}
import scala.concurrent.duration._


/**
 * User: bathily
 * Date: 18/09/13
 */
class AuthzEndpointSpec extends Specification with NoTimeConversions {

  "AuthzEndPoint" should {

    import ExecutionContext.Implicits.global

    "Register a client" in new AuthzEndpoint {
      val client = Await.result(endpoint.register(new BasicOauthClientInfo()), 1 millis)
      (client.id must not).beNull
      (client.secret must not).beNull
      Await.result(repository.find(client.id), 1 millis) must be equalTo Some(client)
    }

    "De register a client" in new AuthzEndpoint {
      val client = Await.result(factory.create(new BasicOauthClientInfo()).flatMap(repository.save), 1 millis)
      Await.result(endpoint.deRegister(client), 1 millis)
      Await.result(repository.find(client.id), 1 millis) must beNone
    }
  }


  """If the request fails the authorization server SHOULD inform the resource owner of the
  error and MUST NOT automatically redirect the user-agent to the
  invalid redirection URI.""" ^
    "if the client identifier is missing" !missingClient ^
    "if the client identifier is invalid" !clientInvalid ^
    "due to a missing redirection URI"    !missingRedirectURI ^
    "due to a missing, invalid, or mismatching redirection URI"
    "due to a missing, invalid, or mismatching redirection URI"

  def missingClient = new AuthzEndpoint {
    import ExecutionContext.Implicits.global

    val r = endpoint.authorize.apply(FakeRequest())
    status(r) must equalTo(BAD_REQUEST)
    contentAsString(r) must equalTo(OAuth.ErrorClientMissing)
  }

  def clientInvalid = new AuthzEndpoint {
    import ExecutionContext.Implicits.global

    val r = endpoint.authorize.apply(FakeRequest().withFormUrlEncodedBody(OAuth.OauthClientId -> "1"))
    status(r) must equalTo(NOT_FOUND)
    contentAsString(r) must equalTo(OAuth.ErrorClientNotFound)
  }

  def missingRedirectURI = new AuthzEndPointWithClients {
    import ExecutionContext.Implicits.global

    val r = endpoint.authorize.apply(FakeRequest().withFormUrlEncodedBody(OAuth.OauthClientId -> ClientWithoutURI))
    status(r) must equalTo(BAD_REQUEST)
    contentAsString(r) must equalTo(OAuth.ErrorRedirectURIMissing)
  }

}

trait AuthzEndpoint extends Scope {
  lazy val factory = new UUIDOauthClientFactory()
  lazy val repository = new InMemoryOauthClientRepository[BasicOauthClient]()
  lazy val endpoint = new endpoints.AuthzEndpoint[BasicOauthClientInfo, BasicOauthClient](factory, repository)
}

trait AuthzEndPointWithClients extends AuthzEndpoint {
  var redirectURI = "http://localhost:9000"
  val ClientWithoutURI = "1"
  val ClientWithURI = "2"

  override lazy val repository = new InMemoryOauthClientRepository[BasicOauthClient](Map(
    ClientWithoutURI -> BasicOauthClient(ClientWithoutURI, ClientWithoutURI),
    ClientWithURI -> BasicOauthClient(ClientWithURI, ClientWithURI, new BasicOauthClientInfo(Some(redirectURI)))
  ))
}