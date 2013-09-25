package fr.njin.playoauth.common.request

import fr.njin.playoauth.common.OAuth


trait TokenRequest {
  def grantType:String
}

case class AuthorizationCodeTokenRequest(code: String, clientId: String, redirectUri: Option[String]) extends TokenRequest {
  def grantType = OAuth.GrantType.AuthorizationCode
}

case class PasswordTokenRequest(username: String, password: String, scope: Option[Seq[String]]) extends TokenRequest {
  def grantType = OAuth.GrantType.Password
}

case class ClientCredentialsTokenRequest(scope: Option[Seq[String]]) extends TokenRequest {
  def grantType = OAuth.GrantType.ClientCredentials
}