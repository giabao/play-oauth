package fr.njin.playoauth.common.domain

import fr.njin.playoauth.common.request.AuthzRequest
import scala.concurrent.Future

/**
 * Represents the permission granted by the resource owner to a client.
 *
 * When issuing a code or a token, the authorization server will ask
 * to the resource owner the permission. The answer of this demand is represented
 * by this permission.
 */
trait OauthPermission {

  /**
   * @return the id of the client of the permission
   */
  def clientId: String

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

class BasicOAuthPermission(val accepted: Boolean,
                           val clientId: String,
                           val scopes: Option[Seq[String]],
                           val redirectUri: Option[String]) extends OauthPermission {

  def authorized(request: AuthzRequest): Boolean = accepted && request.redirectUri == redirectUri
}

/**
 * Repository to retrieve an eventually permission granted by a resource owner to a client
 *
 * The authorization endpoint will search a permission when a client will request a code or a token.
 * Return None if there isn't a permission.
 *
 * params of apply method are: (ownerId, clientId)
 * @tparam P Type of the permission
 */
trait OauthResourceOwnerPermission[P <: OauthPermission]
  extends ((String, String) => Future[Option[P]])
