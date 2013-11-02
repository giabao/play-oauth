package fr.njin.playoauth.common.request

import fr.njin.playoauth.common.OAuth

case class AuthzRequest(responseType: String, clientId: String, redirectUri: Option[String], scope: Option[Seq[String]], state: Option[String])

object AuthzRequest {

  implicit def authzRequest2QueryString(authzRequest: AuthzRequest): Map[String, Seq[String]] = {
    Map(
      OAuth.OauthResponseType -> Seq(OAuth.ResponseType.Code),
      OAuth.OauthClientId -> Seq(authzRequest.clientId)
    ) ++ authzRequest.redirectUri.map(s => OAuth.OauthRedirectUri -> Seq(s)) ++ authzRequest.scope.map(s => OAuth.OauthScope -> Seq(s.mkString(" ")))
  }
}