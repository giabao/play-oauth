import fr.njin.playoauth.common.domain.BasicOauthClientInfo
import fr.njin.playoauth.common.OAuth
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.test._
import play.api.test.Helpers._
import scala.concurrent.{ExecutionContext, Await}
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

    "Register a client" in new Endpoint {
      val client = Await.result(authzEndpoint.register(Seq(OAuth.ResponseType.Code), Seq(), new BasicOauthClientInfo()), timeout)
      (client.id must not).beNull
      (client.secret must not).beNull
      Await.result(repository.find(client.id), 1 millis) must be equalTo Some(client)
    }

    "De register a client" in new Endpoint {
      val client = Await.result(factory.apply(Seq(OAuth.ResponseType.Code), Seq(), new BasicOauthClientInfo()).flatMap(repository.save), timeout)
      Await.result(authzEndpoint.deRegister(client), 1 millis)
      Await.result(repository.find(client.id), 1 millis) must beNone
    }

    "Issues an authorization code and delivers it to the client" in new EndPointWithClients {
      val r = authz.apply(FakeRequest().withFormUrlEncodedBody(
        OAuth.OauthClientId -> ClientWithURI,
        OAuth.OauthResponseType -> OAuth.ResponseType.Code,
        OAuth.OauthState -> AuthorizationState
      ))
      status(r) must equalTo(FOUND)
      val redirection = redirectLocation(r)
      redirection must not beNone
      val parsed = parse(redirection.get)
      url(parsed) must equalTo(RedirectURI)
      val query = parsed.query
      query.param(OAuth.OauthError) must beNone
      query.param(OAuth.OauthCode) must beSome[String]
      query.param(OAuth.OauthState) must beSome(AuthorizationState)
    }

    "Refuse an authorization code for the client" in new EndPointWithClients {
      val r = authz.apply(FakeRequest().withFormUrlEncodedBody(
        OAuth.OauthClientId -> ClientWithURIUnauthorized,
        OAuth.OauthResponseType -> OAuth.ResponseType.Code,
        OAuth.OauthState -> AuthorizationState
      ))
      status(r) must equalTo(FOUND)
      val redirection = redirectLocation(r)
      redirection must not beNone
      val parsed = parse(redirection.get)
      url(parsed) must equalTo(RedirectURI)
      val query = parsed.query
      query.param(OAuth.OauthError) must beSome(OAuth.ErrorCode.AccessDenied)
      query.param(OAuth.OauthCode) must beNone
      query.param(OAuth.OauthState) must beSome(AuthorizationState)
    }
  }


  """If the request fails the authorization server SHOULD inform the resource owner of the
  error and MUST NOT automatically redirect the user-agent to the
  invalid redirection URI.""" >> {

    import ExecutionContext.Implicits.global

    "if the client identifier is missing" in new Endpoint {
      val r = authz.apply(FakeRequest())
      status(r) must equalTo(BAD_REQUEST)
      contentAsString(r) must equalTo(OAuth.ErrorClientMissing)
    }

    "if the client identifier is invalid" in new Endpoint {
      val r = authz.apply(FakeRequest().withFormUrlEncodedBody(OAuth.OauthClientId -> "1"))
      status(r) must equalTo(NOT_FOUND)
      contentAsString(r) must equalTo(OAuth.ErrorClientNotFound)

      val r2 = authz.apply(FakeRequest().withFormUrlEncodedBody(
        OAuth.OauthClientId -> "1",
        OAuth.OauthResponseType -> OAuth.ResponseType.Code
      ))
      status(r2) must equalTo(NOT_FOUND)
      contentAsString(r2) must equalTo(OAuth.ErrorClientNotFound)
    }

    "due to a missing redirection URI" in new EndPointWithClients {
      val r = authz.apply(FakeRequest().withFormUrlEncodedBody(OAuth.OauthClientId -> ClientWithoutURI))
      status(r) must equalTo(BAD_REQUEST)
      contentAsString(r) must equalTo(OAuth.ErrorRedirectURIMissing)
    }

    "due to an invalid redirection URI" in new EndPointWithClients {
      val r = authz.apply(FakeRequest().withFormUrlEncodedBody(
        OAuth.OauthClientId -> ClientWithoutURI,
        OAuth.OauthRedirectUri -> InvalidURI
      ))
      status(r) must equalTo(BAD_REQUEST)
      contentAsString(r) must equalTo(OAuth.ErrorRedirectURIInvalid)
    }

    "due to an mismatching redirection URI" in new EndPointWithClients {
      val r = authz.apply(FakeRequest().withFormUrlEncodedBody(OAuth.OauthClientId -> ClientWithoutURI))
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
          OAuth.OauthClientId -> ImplicitGrantClientWithURI,
          OAuth.OauthResponseType -> OAuth.ResponseType.Code
        )
      )
    ).map(test => invalidRequest(test._1, OAuth.ErrorCode.UnauthorizedClient, test._2))

    Seq(
      "The resource owner or authorization server denied the request." -> Seq(
        FakeRequest().withFormUrlEncodedBody(
          OAuth.OauthClientId -> UnauthorizedClient,
          OAuth.OauthResponseType -> OAuth.ResponseType.Code
        )
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
    name in new EndPointWithClients {
      requests.map{ request =>
        val r = authz.apply(request)
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