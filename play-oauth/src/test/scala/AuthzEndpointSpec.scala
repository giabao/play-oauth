import fr.njin.playoauth.common.OAuth
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import play.api.mvc.{Results, Result, AnyContentAsEmpty}
import play.api.test._
import play.api.test.Helpers._
import scala.concurrent.{Future, ExecutionContext}
import com.netaporter.uri.Uri.parse

import Utils._

/**
 * User: bathily
 * Date: 18/09/13
 */
class AuthzEndpointSpec extends Specification with NoTimeConversions {

  import Constants._

  "AuthzEndPoint" should {

    import ExecutionContext.Implicits.global

    "Issues an authorization code and delivers it to the client" in new EndPointWithClients {
      val r = authz.apply(OauthFakeRequest(
        OAuth.OauthClientId -> ClientWithURI,
        OAuth.OauthResponseType -> OAuth.ResponseType.Code,
        OAuth.OauthState -> AuthorizationState
      ))
      status(r) must equalTo(FOUND)
      val redirection = redirectLocation(r)
      (redirection must not).beNone
      val parsed = parse(redirection.get)
      url(parsed) must equalTo(RedirectURI)
      val query = parsed.query
      query.param(OAuth.OauthError) must beNone
      query.param(OAuth.OauthCode) must beSome[String]
      query.param(OAuth.OauthState) must beSome(AuthorizationState)
    }

    "Refuse an authorization code for the client" in new EndPointWithClients {
      val r = authz.apply(OauthFakeRequest(
        OAuth.OauthClientId -> ClientWithURIUnauthorized,
        OAuth.OauthResponseType -> OAuth.ResponseType.Code,
        OAuth.OauthState -> AuthorizationState
      ))
      status(r) must equalTo(FOUND)
      val redirection = redirectLocation(r)
      (redirection must not).beNone
      val parsed = parse(redirection.get)
      url(parsed) must equalTo(RedirectURI)
      val query = parsed.query
      query.param(OAuth.OauthError) must beSome(OAuth.ErrorCode.AccessDenied)
      query.param(OAuth.OauthCode) must beNone
      query.param(OAuth.OauthState) must beSome(AuthorizationState)
    }

    "Issues an access token  and delivers it to the client" in new EndPointWithClients {
      val r = authz.apply(OauthFakeRequest(
        OAuth.OauthClientId -> ClientWithURI,
        OAuth.OauthResponseType -> OAuth.ResponseType.Token,
        OAuth.OauthState -> AuthorizationState
      ))
      status(r) must equalTo(FOUND)
      val redirection = redirectLocation(r)
      (redirection must not).beNone
      val parsed = parse(redirection.get)
      url(parsed) must equalTo(RedirectURI)

      (parsed.fragment must not).beNone

      val query = parsed.fragment.map {
        _.split("&").map { s =>
          val arr = s.split("=")
          arr(0) -> arr(1)
        }.toMap
      }

      def p(key: String): Option[String] = query.flatMap(_.get(key))

      p(OAuth.OauthError) must beNone
      p(OAuth.OauthAccessToken) must beSome[String]
      p(OAuth.OauthTokenType) must beSome[String]
      p(OAuth.OauthRefreshToken) must beNone
      p(OAuth.OauthState) must beSome(AuthorizationState)
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
      val r = authz.apply(OauthFakeRequest(OAuth.OauthClientId -> "1"))
      status(r) must equalTo(NOT_FOUND)
      contentAsString(r) must equalTo(OAuth.ErrorClientNotFound)

      val r2 = authz.apply(OauthFakeRequest(
        OAuth.OauthClientId -> "1",
        OAuth.OauthResponseType -> OAuth.ResponseType.Code
      ))
      status(r2) must equalTo(NOT_FOUND)
      contentAsString(r2) must equalTo(OAuth.ErrorClientNotFound)
    }

    "due to a missing redirection URI" in new EndPointWithClients {
      val r = authz.apply(OauthFakeRequest(OAuth.OauthClientId -> ClientWithoutURI))
      status(r) must equalTo(BAD_REQUEST)
      contentAsString(r) must equalTo(OAuth.ErrorRedirectURIMissing)
    }

    "due to an invalid redirection URI" in new EndPointWithClients {
      val r = authz.apply(OauthFakeRequest(
        OAuth.OauthClientId -> ClientWithoutURI,
        OAuth.OauthRedirectUri -> InvalidURI
      ))
      status(r) must equalTo(BAD_REQUEST)
      contentAsString(r) must equalTo(OAuth.ErrorRedirectURIInvalid)
    }

    "due to an mismatching redirection URI" in new EndPointWithClients {
      val r = authz.apply(OauthFakeRequest(OAuth.OauthClientId -> ClientWithoutURI))
      status(r) must equalTo(BAD_REQUEST)
      contentAsString(r) must equalTo(OAuth.ErrorRedirectURIMissing)
    }

    """The authorization server encountered an unexpected
       condition that prevented it from fulfilling the request.
       (This error code is needed because a 500 Internal Server
       Error HTTP status code cannot be returned to the client
       via an HTTP redirect.)""" in new EndPointWithClients {

      import ExecutionContext.Implicits.global

      val r = authzEndpoint.authorize(r => throw new Exception())(
        (ar,c) => r => Future.successful(Results.Unauthorized("")),
        (ar,c) => r => Future.successful(Results.Forbidden("")),
        error => Future.successful(Results.NotFound(error)),
        error => Future.successful(Results.BadRequest(error))
      ).apply(OauthFakeRequest(
        OAuth.OauthClientId -> ClientWithURI,
        OAuth.OauthResponseType -> OAuth.ResponseType.Code,
        OAuth.OauthState -> AuthorizationState
      ))
      checkError(r, OAuth.ErrorCode.ServerError)
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
        OauthFakeRequest(
          OAuth.OauthClientId -> ClientWithURI
        )
      ),
      "includes an invalid parameter value" -> Seq(
        //TODO What's an invalid parameter value?
        /*
        OauthFakeRequest(
          OAuth.OauthClientId -> ClientWithURI,
          OAuth.OauthResponseType -> "unknown_code"
        )
        */
      ),
      "includes a parameter more than once" -> Seq(
        OauthFakeRequest(
          OAuth.OauthClientId -> ClientWithURI,
          OAuth.OauthResponseType -> OAuth.ResponseType.Code,
          OAuth.OauthClientId -> ClientWithURI
        )
      )
    ).map(test => invalidRequest(test._1, OAuth.ErrorCode.InvalidRequest, test._2))

    Seq(
      "The client is not authorized to request an authorization code using this method." -> Seq(
        OauthFakeRequest(
          OAuth.OauthClientId -> ImplicitGrantClientWithURI,
          OAuth.OauthResponseType -> OAuth.ResponseType.Code
        )
      )
    ).map(test => invalidRequest(test._1, OAuth.ErrorCode.UnauthorizedClient, test._2))

    Seq(
      "The resource owner denied the request." -> Seq(
        OauthFakeRequest(
          OAuth.OauthClientId -> UnauthorizedClient,
          OAuth.OauthResponseType -> OAuth.ResponseType.Code
        )
      ),
      "The authorization server denied the request." -> Seq(
        OauthFakeRequest(
          OAuth.OauthClientId -> BlockedClient,
          OAuth.OauthResponseType -> OAuth.ResponseType.Code
        )
      )
    ).map(test => invalidRequest(test._1, OAuth.ErrorCode.AccessDenied, test._2))

    Seq(
      "The authorization server does not support obtaining an authorization code using this method." -> Seq(
        OauthFakeRequest(
          OAuth.OauthClientId -> ClientWithURI,
          OAuth.OauthResponseType -> "unknown_code"
        )
      )
    ).map(test => invalidRequest(test._1, OAuth.ErrorCode.UnsupportedResponseType, test._2))

    Seq(
      "The requested scope is invalid, unknown, or malformed." -> Seq(
        OauthFakeRequest(
          OAuth.OauthClientId -> ClientWithURI,
          OAuth.OauthResponseType -> OAuth.ResponseType.Code,
          OAuth.OauthScope -> "unknown_scope"
        )
      )
    ).map(test => invalidRequest(test._1, OAuth.ErrorCode.InvalidScope, test._2))
  }

  def checkError(result: Future[Result], waitingCode: String) = {
    status(result) must equalTo(FOUND)
    val redirection = redirectLocation(result)
    (redirection must not).beNone
    val parsed = parse(redirection.get)
    url(parsed) must equalTo(RedirectURI)
    val query = parsed.query
    query.param(OAuth.OauthError) must beSome(waitingCode)
  }

  def invalidRequest(name:String, waitingCode: String, requests: Seq[FakeRequest[AnyContentAsEmpty.type]])(implicit ec:ExecutionContext) = {
    name in new EndPointWithClients {
      requests.map{request => checkError(authz.apply(request), waitingCode)}
    }
  }


}
