package fr.njin.playoauth.common.request

case class AuthzRequest(responseType: String, clientId: String, redirectUri: Option[String], scope: Option[Seq[String]], state: Option[String])