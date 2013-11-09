package fr.njin.playoauth.common
import scala.concurrent.duration._

object OAuth {

  /**
   * The recommended maximum authorization code lifetime
   */
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
    val RefreshToken = "refresh_token"
    val All = Seq(AuthorizationCode, Password, ClientCredentials, RefreshToken)
  }

  val OauthResponseType = "response_type"
  val OauthClientId = "client_id"
  val OauthClientSecret = "client_secret"
  val OauthRedirectUri = "redirect_uri"
  val OauthGrantType = "grant_type"
  val OauthUsername = "username"
  val OauthPassword = "password"

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

  val ErrorClientMissing = "error.missing.clientId"
  val ErrorClientNotFound = "error.client.notfound"
  val ErrorClientCredentialsDontMatch = "error.client.credentials_dont_match"
  val ErrorRedirectURIMissing = "error.redirectUri.missing"
  val ErrorRedirectURIInvalid = "error.redirectUri.invalid"
  val ErrorInvalidScope = "error.invalid.scope"
  val ErrorUnauthorizedResponseType = "error.unauthorized.response_type"
  val ErrorUnauthorizedGrantType = "error.unauthorized.grant_type"
  val ErrorUnsupportedGrantType = "error.unsupported.grant_type"
  val ErrorUnknownAuthorizationCode = "error.unknown.authorization_code"
  val ErrorExpiredAuthorizationCode = "error.expired.authorization_code"
  val ErrorRevokedAuthorizationCode = "error.revoked.authorization_code"
  val ErrorAlreadyConsumedAuthorizationCode = "error.already_consumed.authorization_code"
  val ErrorClientNotMatch = "error.client.not_match"
  val ErrorRedirectURINotMatch = "error.redirectUri.not_match"
  val ErrorMultipleParameters = "error.multiple.parameters"
  val ErrorInvalidCredentials = "error.invalid.credentials"

  object ErrorCode {
    val InvalidRequest = "invalid_request"
    val InvalidClient = "invalid_client"
    val InvalidGrant = "invalid_grant"
    val UnauthorizedClient = "unauthorized_client"
    val AccessDenied = "access_denied"
    val UnsupportedResponseType = "unsupported_response_type"
    val UnsupportedGrantType = "unsupported_grant_type"
    val InvalidScope = "invalid_scope"
    val ServerError = "server_error"
  }

}
