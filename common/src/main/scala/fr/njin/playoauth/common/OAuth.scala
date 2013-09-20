package fr.njin.playoauth.common
import types._

/**
 * User: bathily
 * Date: 17/09/13
 */
object OAuth {


  object ResponseType {
    val Code = "code"
    val Token = "token"
    val All = Seq(Code, Token)
  }

  val OauthResponseType = "response_type"
  val OauthClientId = "client_id"
  val OauthClientSecret = "client_secret"
  val OauthRedirectUri = "redirect_uri"
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
  /*
  val OAUTH_GRANT_TYPE = "grant_type"

  val OAUTH_HEADER_NAME = "Bearer"
  */
  /*
  //Authorization response params
  val OAUTH_CODE = "code"
  val OAUTH_ACCESS_TOKEN = "access_token"
  val OAUTH_EXPIRES_IN = "expires_in"
  val OAUTH_REFRESH_TOKEN = "refresh_token"

  val OAUTH_TOKEN_TYPE = "token_type"

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

  object ErrorCode {
    val InvalidRequest = "invalid_request"
    val UnauthorizedClient = "unauthorized_client"
    val AccessDenied = "access_denied"
    val UnsupportedResponseType = "unsupported_response_type"
    val InvalidScope = "invalid_scope"
  }

}
