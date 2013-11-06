package fr.njin.playoauth.common.domain

import fr.njin.playoauth.common.request.AuthzRequest

/**
 * Represents the permission granted by the resource owner to a client.
 *
 * When issuing a code or a token, the authorization server will ask
 * to the resource owner the permission. The answer of this demand is represented
 * by this permission.
 *
 * @tparam C Type of the client
 */
trait OauthPermission[C <: OauthClient] {

  /**
   * @return the client of the permission
   */
  def client: C

  /**
   * @return the scopes accepted by the resource owner
   */
  def scopes: Option[Seq[String]]

  /**
   * @return the redirect url of the request
   */
  def redirectUri : Option[String]

  /**
   * @param request the authorization request
   * @return true if the resource owner authorize the request
   */
  def authorized(request: AuthzRequest): Boolean
}

class BasicOAuthPermission[C <: OauthClient](val accepted: Boolean,
                                             val client: C,
                                             val scopes: Option[Seq[String]],
                                             val redirectUri: Option[String]) extends OauthPermission[C] {

  def authorized(request: AuthzRequest): Boolean = accepted && request.redirectUri == redirectUri
}