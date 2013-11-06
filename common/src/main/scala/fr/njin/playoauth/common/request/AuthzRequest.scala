package fr.njin.playoauth.common.request

import fr.njin.playoauth.common.OAuth

/**
 * The authorization request.
 *
 * Represents the request made by a client in order to obtain a code or a token
 *
 * @param responseType The response type of the request. See [[fr.njin.playoauth.common.OAuth.ResponseType]]
 * @param clientId The id of the client of the request
 * @param redirectUri The redirection url of the request
 * @param scopes The scopes of the request
 * @param state The state of the request
 */
case class AuthzRequest(responseType: String,
                        clientId: String,
                        redirectUri: Option[String],
                        scopes: Option[Seq[String]],
                        state: Option[String])

/**
 * Implicit conversion for [[fr.njin.playoauth.common.request.AuthzRequest]]
 */
object AuthzRequest {

  implicit def authzRequest2QueryString(authzRequest: AuthzRequest): Map[String, Seq[String]] = {
    Map(
      OAuth.OauthResponseType -> Seq(OAuth.ResponseType.Code),
      OAuth.OauthClientId -> Seq(authzRequest.clientId)
    ) ++ authzRequest.redirectUri.map(s => OAuth.OauthRedirectUri -> Seq(s)) ++ authzRequest.scopes.map(s => OAuth.OauthScope -> Seq(s.mkString(" ")))
  }
}