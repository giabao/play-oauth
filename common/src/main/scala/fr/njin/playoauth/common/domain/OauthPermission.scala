package fr.njin.playoauth.common.domain

import fr.njin.playoauth.common.request.AuthzRequest

trait OauthPermission[C <: OauthClient] {

  def client: C
  def scope: Option[Seq[String]]
  def redirectUri : Option[String]

  def authorized(request: AuthzRequest): Boolean
}

class BasicOAuthPermission[C <: OauthClient](val accepted: Boolean,
                                                              val client: C,
                                                              val scope: Option[Seq[String]],
                                                              val redirectUri: Option[String]) extends OauthPermission[C] {

  def authorized(request: AuthzRequest): Boolean = accepted && request.redirectUri == redirectUri
}