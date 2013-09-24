package fr.njin.playoauth.common
import scala.concurrent.duration._

/**
 * User: bathily
 * Date: 17/09/13
 */
object OAuth {


  val MaximumLifetime = 10.minutes

  object ResponseType {
    val Code = "code"
    val Token = "token"
    val All = Seq(Code, Token)
  }

  object GrantType {
    val AuthorizationCode = "authorization_code"
    val Password = "password"
    val ClientCredentials = "client_credentials"
    val All = Seq(AuthorizationCode, Password, ClientCredentials)
  }

  val OauthResponseType = "response_type"
  val OauthClientId = "client_id"
  val OauthClientSecret = "client_secret"
  val OauthRedirectUri = "redirect_uri"
  val OauthGrantType = "grant_type"

  /*
  val OAUTH_USERNAME = "username"
  val OAUTH_PASSWORD = "password"
  val OAUTH_ASSERTION_TYPE = "assertion_type"
  val OAUTH_ASSERTION = "assertion"
  */
  val OauthScope = "scope"
  val OauthState = "state"
  val OauthCode = "code"
  val OauthError = "error"
  val OauthErrorDescription = "error_description"
  val OauthErrorUri = "error_uri"
  val OauthAccessToken = "access_token"
  val OauthTokenType = "token_type"
  val OauthExpiresIn = "expires_in"
  val OauthRefreshToken = "refresh_token"

  /*


  val OAUTH_HEADER_NAME = "Bearer"
  */
  /*
  //Authorization response params
  val OAUTH_CODE = "code"

  val OAUTH_TOKEN = "oauth_token"

  val OAUTH_TOKEN_DRAFT_0 = "access_token"
  val OAUTH_BEARER_TOKEN = "access_token"

  val DEFAULT_PARAMETER_STYLE = Header
  val DEFAULT_TOKEN_TYPE = Bearer

  val OAUTH_VERSION_DIFFER = "oauth_signature_method"
  */

  var ErrorClientMissing = "error.missing.clientId"
  val ErrorClientNotFound = "error.client.notfound"
  val ErrorRedirectURIMissing = "error.redirectUri.missing"
  val ErrorRedirectURIInvalid = "error.redirectUri.invalid"
  val ErrorInvalidScope = "error.invalid.scope"
  val ErrorUnauthorizedResponseType = "error.unauthorized.response_type"
  val ErrorUnauthorizedGrantType = "error.unauthorized.grant_type"
  val ErrorUnsupportedGrantType = "error.unsupported.grant_type"
  val ErrorUnknownAuthorizationCode = "error.unknown.authorization_code"
  val ErrorExpiredAuthorizationCode = "error.expired.authorization_code"
  val ErrorRevokedAuthorizationCode = "error.revoked.authorization_code"
  val ErrorClientNotMatch = "error.client.not_match"
  val ErrorRedirectURINotMatch = "error.redirectUri.not_match"
  val ErrorMultipleParameters = "error.multiple.parameters"

  object ErrorCode {
    val InvalidRequest = "invalid_request"
    val InvalidClient = "invalid_client"
    val InvalidGrant = "invalid_grant"
    val UnauthorizedClient = "unauthorized_client"
    val AccessDenied = "access_denied"
    val UnsupportedResponseType = "unsupported_response_type"
    val UnsupportedGrantType = "unsupported_grant_type"
    val InvalidScope = "invalid_scope"
  }

}
