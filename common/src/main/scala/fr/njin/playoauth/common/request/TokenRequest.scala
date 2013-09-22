package fr.njin.playoauth.common.request

case class TokenRequest(grantType: String, code: String, clientId: String, redirectUri: Option[String])