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
class TokenEndpointSpec extends Specification with NoTimeConversions {

  import Constants._

  /*
  "TokenEndPoint" should {

    import ExecutionContext.Implicits.global

    "Register a client" in new Endpoint {
      val client = Await.result(endpoint.register(Seq(OAuth.ResponseType.Code), new BasicOauthClientInfo()), timeout)
      (client.id must not).beNull
      (client.secret must not).beNull
      Await.result(repository.find(client.id), 1 millis) must be equalTo Some(client)
    }

    "De register a client" in new Endpoint {
      val client = Await.result(factory.apply(Seq(OAuth.ResponseType.Code), new BasicOauthClientInfo()).flatMap(repository.save), timeout)
      Await.result(endpoint.deRegister(client), 1 millis)
      Await.result(repository.find(client.id), 1 millis) must beNone
    }

    "Issues an authorization code and delivers it to the client" in new EndPointWithClients {
      val r = authOk.apply(FakeRequest().withFormUrlEncodedBody(
        OAuth.OauthClientId -> ClientWithURI,
        OAuth.OauthResponseType -> OAuth.ResponseType.Code,
        OAuth.OauthState -> authorizationState
      ))
      status(r) must equalTo(FOUND)
      val redirection = redirectLocation(r)
      redirection must not beNone
      val parsed = parse(redirection.get)
      url(parsed) must equalTo(RedirectURI)
      val query = parsed.query
      query.param(OAuth.OauthError) must beNone
      query.param(OAuth.OauthCode) must beSome(authorizationCode)
      query.param(OAuth.OauthState) must beSome(authorizationState)
    }

    "Refuse an authorization code for the client" in new EndPointWithClients {
      val r = authDenied.apply(FakeRequest().withFormUrlEncodedBody(
        OAuth.OauthClientId -> ClientWithURI,
        OAuth.OauthResponseType -> OAuth.ResponseType.Code,
        OAuth.OauthState -> authorizationState
      ))
      status(r) must equalTo(FOUND)
      val redirection = redirectLocation(r)
      redirection must not beNone
      val parsed = parse(redirection.get)
      url(parsed) must equalTo(RedirectURI)
      val query = parsed.query
      query.param(OAuth.OauthError) must beSome(OAuth.ErrorCode.AccessDenied)
      query.param(OAuth.OauthCode) must beNone
      query.param(OAuth.OauthState) must beSome(authorizationState)
    }
  }
  */


  """The authorization server responds with an HTTP 400 (Bad Request)
   status code (unless specified otherwise)""" >> {

    import ExecutionContext.Implicits.global

    "The request is missing a required parameter" in new EndPointWithClients {
      Seq(
        FakeRequest(),
        FakeRequest().withFormUrlEncodedBody(
          OAuth.OauthGrantType -> OAuth.GrantType.AuthorizationCode,
          OAuth.OauthCode -> AuthorizationCode,
          OAuth.OauthRedirectUri -> RedirectURI
        ),
        FakeRequest().withFormUrlEncodedBody(
          OAuth.OauthClientId -> ClientWithCode,
          OAuth.OauthCode -> AuthorizationCode,
          OAuth.OauthRedirectUri -> RedirectURI
        ),
        FakeRequest().withFormUrlEncodedBody(
          OAuth.OauthClientId -> ClientWithCode,
          OAuth.OauthGrantType -> OAuth.GrantType.AuthorizationCode,
          OAuth.OauthRedirectUri -> RedirectURI
        ),
        //TODO
        FakeRequest().withFormUrlEncodedBody(
          OAuth.OauthClientId -> ClientWithCode,
          OAuth.OauthGrantType -> OAuth.GrantType.AuthorizationCode,
          OAuth.OauthCode -> AuthorizationCode
        )
      ).map(request => {
        val r = token.apply(request)
        status(r) must equalTo(BAD_REQUEST)
        (contentAsJson(r) \ OAuth.OauthError).as[String] must equalTo(OAuth.ErrorCode.InvalidRequest)
      })
    }

    "includes an unsupported parameter value (other than grant type)" in new EndPointWithClients {
      val r = token.apply(FakeRequest().withFormUrlEncodedBody(
        OAuth.OauthClientId -> ClientWithCode,
        OAuth.OauthGrantType -> "unknown_grant_type",
        OAuth.OauthCode -> AuthorizationCode
      ))
      status(r) must equalTo(BAD_REQUEST)
      (contentAsJson(r) \ OAuth.OauthError).as[String] must equalTo(OAuth.ErrorCode.UnsupportedGrantType)
    }

    "repeats a parameter" in new EndPointWithClients {
      val r = token.apply(FakeRequest().withFormUrlEncodedBody(
        OAuth.OauthClientId -> ClientWithCode,
        OAuth.OauthGrantType -> OAuth.GrantType.AuthorizationCode,
        OAuth.OauthCode -> AuthorizationCode,
        OAuth.OauthCode -> AuthorizationCode,
        OAuth.OauthRedirectUri -> RedirectURI
      ))
      status(r) must equalTo(BAD_REQUEST)
      (contentAsJson(r) \ OAuth.OauthError).as[String] must equalTo(OAuth.ErrorCode.InvalidRequest)
    }

    "includes multiple credentials" in new EndPointWithClients {
      failure("TODO")
    }

    "utilizes more than one mechanism for authenticating the client" in new EndPointWithClients {
      failure("TODO")
    }

    """Client authentication failed (e.g., unknown client, no
    client authentication included, or unsupported
    authentication method)""" in new EndPointWithClients {
      val r = token.apply(FakeRequest().withFormUrlEncodedBody(
        OAuth.OauthClientId -> "unknown_client",
        OAuth.OauthGrantType -> OAuth.GrantType.AuthorizationCode,
        OAuth.OauthCode -> AuthorizationCode,
        OAuth.OauthRedirectUri -> RedirectURI
      ))
      status(r) must equalTo(UNAUTHORIZED)
      println(contentAsJson(r))
      (contentAsJson(r) \ OAuth.OauthError).as[String] must equalTo(OAuth.ErrorCode.InvalidClient)
    }

    """The provided authorization grant is invalid, expired, revoked,
       does not match the redirection URI used in the authorization request,
       or was issued to another client.""" in new EndPointWithClients {
      val r = token.apply(FakeRequest().withFormUrlEncodedBody(
        OAuth.OauthClientId -> ClientWithCode,
        OAuth.OauthGrantType -> OAuth.GrantType.AuthorizationCode,
        OAuth.OauthCode -> "unknown_code",
        OAuth.OauthRedirectUri -> RedirectURI
      ))
      status(r) must equalTo(BAD_REQUEST)
      println(contentAsJson(r))
      (contentAsJson(r) \ OAuth.OauthError).as[String] must equalTo(OAuth.ErrorCode.InvalidGrant)
    }

    "The authenticated client is not authorized to use this authorization grant type." in new EndPointWithClients {
      val r = token.apply(FakeRequest().withFormUrlEncodedBody(
        OAuth.OauthClientId -> ClientWithCode,
        OAuth.OauthGrantType -> OAuth.GrantType.ClientCredentials,
        OAuth.OauthCode -> AuthorizationCode,
        OAuth.OauthRedirectUri -> RedirectURI
      ))
      status(r) must equalTo(BAD_REQUEST)
      (contentAsJson(r) \ OAuth.OauthError).as[String] must equalTo(OAuth.ErrorCode.UnauthorizedClient)
    }

    "The authorization grant type is not supported by the authorization server." in new EndPointWithClients {
      failure("TODO")
    }

    "The requested scope is invalid, unknown, malformed, or exceeds the scope granted by the resource owner." in new EndPointWithClients {
      failure("TODO")
    }

  }

  /*
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
  */
}



