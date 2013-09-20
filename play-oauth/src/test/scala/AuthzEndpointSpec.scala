import fr.njin.playoauth.as.endpoints
import fr.njin.playoauth.as.endpoints.{InMemoryOauthScopeRepository, InMemoryOauthClientRepository, UUIDOauthClientFactory}
import fr.njin.playoauth.common.client.{BasicOauthScope, BasicOauthClient, BasicOauthClientInfo}
import fr.njin.playoauth.common.OAuth
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.time.NoTimeConversions
import play.api.mvc.{Results, AnyContentAsFormUrlEncoded}
import play.api.test._
import play.api.test.Helpers._
import scala.concurrent.{Future, ExecutionContext, Await}
import scala.concurrent.duration._
import com.github.theon.uri.Uri.parse
import Utils._

/**
 * User: bathily
 * Date: 18/09/13
 */
class AuthzEndpointSpec extends Specification with NoTimeConversions {

  import Constants._

  "AuthzEndPoint" should {

    import ExecutionContext.Implicits.global

    "Register a client" in new AuthzEndpoint {
      val client = Await.result(endpoint.register(new BasicOauthClientInfo()), timeout)
      (client.id must not).beNull
      (client.secret must not).beNull
      Await.result(repository.find(client.id), 1 millis) must be equalTo Some(client)
    }

    "De register a client" in new AuthzEndpoint {
      val client = Await.result(factory.apply(new BasicOauthClientInfo()).flatMap(repository.save), timeout)
      Await.result(endpoint.deRegister(client), 1 millis)
      Await.result(repository.find(client.id), 1 millis) must beNone
    }
  }


  """If the request fails the authorization server SHOULD inform the resource owner of the
  error and MUST NOT automatically redirect the user-agent to the
  invalid redirection URI.""" >> {

    import ExecutionContext.Implicits.global

    "if the client identifier is missing" in new AuthzEndpoint {
      val r = authOk.apply(FakeRequest())
      status(r) must equalTo(BAD_REQUEST)
      contentAsString(r) must equalTo(OAuth.ErrorClientMissing)
    }

    "if the client identifier is invalid" in new AuthzEndpoint {
      val r = authOk.apply(FakeRequest().withFormUrlEncodedBody(OAuth.OauthClientId -> "1"))
      status(r) must equalTo(NOT_FOUND)
      contentAsString(r) must equalTo(OAuth.ErrorClientNotFound)

      val r2 = authOk.apply(FakeRequest().withFormUrlEncodedBody(
        OAuth.OauthClientId -> "1",
        OAuth.OauthResponseType -> OAuth.ResponseType.Code
      ))
      status(r2) must equalTo(NOT_FOUND)
      contentAsString(r2) must equalTo(OAuth.ErrorClientNotFound)
    }

    "due to a missing redirection URI" in new AuthzEndPointWithClients {
      val r = authOk.apply(FakeRequest().withFormUrlEncodedBody(OAuth.OauthClientId -> ClientWithoutURI))
      status(r) must equalTo(BAD_REQUEST)
      contentAsString(r) must equalTo(OAuth.ErrorRedirectURIMissing)
    }

    "due to an invalid redirection URI" in new AuthzEndPointWithClients {
      val r = authOk.apply(FakeRequest().withFormUrlEncodedBody(
        OAuth.OauthClientId -> ClientWithoutURI,
        OAuth.OauthRedirectUri -> InvalidURI
      ))
      status(r) must equalTo(BAD_REQUEST)
      contentAsString(r) must equalTo(OAuth.ErrorRedirectURIInvalid)
    }

    "due to an mismatching redirection URI" in new AuthzEndPointWithClients {
      val r = authOk.apply(FakeRequest().withFormUrlEncodedBody(OAuth.OauthClientId -> ClientWithoutURI))
      status(r) must equalTo(BAD_REQUEST)
      contentAsString(r) must equalTo(OAuth.ErrorRedirectURIMissing)
    }
  }

  """ If the resource owner denies the access request or if the request
  fails for reasons other than a missing or invalid redirection URI,
  the authorization server informs the client by adding the following
  parameters to the query component of the redirection URI using the
  "application/x-www-form-urlencoded" format""" ^ {

    import ExecutionContext.Implicits.global

    Seq(
      "The request is missing a required parameter" -> Seq(
        FakeRequest().withFormUrlEncodedBody(
          OAuth.OauthClientId -> ClientWithURI
        )
      ),
      "includes an invalid parameter value" -> Seq(
        //TODO What's an invalid parameter value?
        /*
        FakeRequest().withFormUrlEncodedBody(
          OAuth.OauthClientId -> ClientWithURI,
          OAuth.OauthResponseType -> "unknown_code"
        )
        */
      ),
      "includes a parameter more than once" -> Seq(
        FakeRequest().withFormUrlEncodedBody(
          OAuth.OauthClientId -> ClientWithURI,
          OAuth.OauthResponseType -> OAuth.ResponseType.Code,
          OAuth.OauthClientId -> ClientWithURI
        )
      )
    ).map(test => invalidRequest(test._1, OAuth.ErrorCode.InvalidRequest, test._2))

    Seq(
      "The client is not authorized to request an authorization code using this method." -> Seq(
        FakeRequest().withFormUrlEncodedBody(
          OAuth.OauthClientId -> UnauthorizedClient,
          OAuth.OauthResponseType -> OAuth.ResponseType.Code
        )
      )
    ).map(test => invalidRequest(test._1, OAuth.ErrorCode.UnauthorizedClient, test._2))

    Seq(
      "The resource owner or authorization server denied the request." -> Seq(
        //TODO
        /*
        FakeRequest().withFormUrlEncodedBody(
          OAuth.OauthClientId -> ClientWithURI,
          OAuth.OauthResponseType -> OAuth.ResponseType.Code
        )
        */
      )
    ).map(test => invalidRequest(test._1, OAuth.ErrorCode.AccessDenied, test._2))

    Seq(
      "The authorization server does not support obtaining an authorization code using this method." -> Seq(
        FakeRequest().withFormUrlEncodedBody(
          OAuth.OauthClientId -> ClientWithURI,
          OAuth.OauthResponseType -> "unknown_code"
        )
      )
    ).map(test => invalidRequest(test._1, OAuth.ErrorCode.UnsupportedResponseType, test._2))

    Seq(
      "The requested scope is invalid, unknown, or malformed." -> Seq(
        FakeRequest().withFormUrlEncodedBody(
          OAuth.OauthClientId -> ClientWithURI,
          OAuth.OauthResponseType -> OAuth.ResponseType.Code,
          OAuth.OauthScope -> "unknown_scope"
        )
      )
    ).map(test => invalidRequest(test._1, OAuth.ErrorCode.InvalidScope, test._2))
  }

  def invalidRequest(name:String, waitingCode: String, requests: Seq[FakeRequest[AnyContentAsFormUrlEncoded]])(implicit ec:ExecutionContext) = {
    name in new AuthzEndPointWithClients {
      requests.map{ request =>
        val r = authOk.apply(request)
        status(r) must equalTo(FOUND)
        val redirection = redirectLocation(r)
        redirection must not beNone
        val parsed = parse(redirection.get)
        url(parsed) must equalTo(RedirectURI)
        val query = parsed.query
        query.param(OAuth.OauthError) must beSome(waitingCode)
      }
    }
  }
}

object Constants {
  val RedirectURI = "http://localhost:9000/"
  val InvalidURI = "localhost:9000"
  val ClientWithoutURI = "1"
  val ClientWithURI = "2"
  val ClientWithInvalidURI = "3"
  val UnauthorizedClient = "4"
}


trait AuthzEndpoint extends Scope {
  val timeout = 1 seconds
  lazy val factory = new UUIDOauthClientFactory()
  lazy val repository = new InMemoryOauthClientRepository[BasicOauthClient]()
  lazy val scopeRepository = new InMemoryOauthScopeRepository[BasicOauthScope]()
  lazy val endpoint = new endpoints.AuthzEndpoint[BasicOauthClientInfo, BasicOauthClient, BasicOauthScope](factory, repository, scopeRepository)

  lazy val authOk = endpoint.authorize((authzRequest, oauthClient) => request => {Future.successful(Results.Ok)})
}

trait AuthzEndPointWithClients extends AuthzEndpoint {

  import Constants._

  override lazy val repository = new InMemoryOauthClientRepository[BasicOauthClient](Map(
    ClientWithoutURI -> BasicOauthClient(ClientWithoutURI, ClientWithoutURI),
    ClientWithURI -> BasicOauthClient(ClientWithURI, ClientWithURI, new BasicOauthClientInfo(Some(RedirectURI))),
    ClientWithInvalidURI -> BasicOauthClient(ClientWithInvalidURI, ClientWithInvalidURI, new BasicOauthClientInfo(Some(InvalidURI))),
    UnauthorizedClient -> BasicOauthClient(UnauthorizedClient, UnauthorizedClient, new BasicOauthClientInfo(Some(RedirectURI), authorized = false))
  ))
  override lazy val scopeRepository: InMemoryOauthScopeRepository[BasicOauthScope] = new InMemoryOauthScopeRepository[BasicOauthScope](Map(
    "scope1" -> new BasicOauthScope("scope1"),
    "scope2" -> new BasicOauthScope("scope2")
  ))
}