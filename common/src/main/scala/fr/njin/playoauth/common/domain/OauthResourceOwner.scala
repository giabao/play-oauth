package fr.njin.playoauth.common.domain

import scala.concurrent.Future


/**
 * Represents the oauth2 resource owner
 */
trait OauthResourceOwner

/**
 * Repository to retrieve an eventually permission granted by a resource owner to a client
 *
 * The authorization endpoint will search a permission when a client will request a code or a token.
 * Return None if there isn't a permission.
 *
 * @tparam RO Type of the resource owner
 * @tparam C Type of the client
 * @tparam P Type of the permission
 */
trait OauthResourceOwnerPermission[RO <: OauthResourceOwner, C <: OauthClient, P <: OauthPermission[C]]
  extends ((RO, C) => Future[Option[P]])
