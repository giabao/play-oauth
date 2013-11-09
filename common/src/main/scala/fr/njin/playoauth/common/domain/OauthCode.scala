package fr.njin.playoauth.common.domain

import fr.njin.playoauth.common.OAuth
import scala.concurrent.Future

/**
 * Represents the oauth2 code issuing by the authorization server
 *
 * An oauth2 code is issuing for a resource owner and a client. Its value must be unique
 *
 * @tparam RO Type of the resource owner [[fr.njin.playoauth.common.domain.OauthResourceOwner]]
 * @tparam C  Type of the client [[fr.njin.playoauth.common.domain.OauthClient]]
 */
trait OauthCode[RO <: OauthResourceOwner, C <: OauthClient] {

  /**
   * @return the value of the code. Must be unique.
   */
  def value: String

  /**
   * @return the resource owner of the code.
   */
  def owner: RO

  /**
   * @return the client of the code
   */
  def client:C

  /**
   * @return the expiry timestamp of the code
   */
  def issueAt: Long

  /**
   * @return the life time of the code in milliseconds
   */
  def expireIn: Long

  /**
   * @return true if the code is revoked
   *
   * The code is revoked by the authorization endpoint before issuing a token
   */
  def revoked: Boolean

  /**
   * @return the redirectUri specified by the client when requesting the code
   */
  def redirectUri: Option[String]

  /**
   * @return the scopes of the code
   */
  def scopes: Option[Seq[String]]
}

/**
 * Factory used to create a code
 *
 * @tparam CO Type of the code
 * @tparam RO Type of the resource owner
 * @tparam C Type of the client
 */
trait OauthCodeFactory[CO <: OauthCode[RO, C], RO <: OauthResourceOwner, C <: OauthClient] {
  def apply(owner:RO, client:C, redirectUri: Option[String], scopes: Option[Seq[String]]): Future[CO]
}

/**
 * Repository used to retrieve a code by its value and to revoke a code
 *
 * @tparam CO Type of the code
 * @tparam RO Type of the resource owner
 * @tparam C Type of the clien
 */
trait OauthCodeRepository[CO <: OauthCode[RO, C], RO <: OauthResourceOwner, C <: OauthClient] {
  def find(value: String): Future[Option[CO]]
  def revoke(value: String): Future[Option[CO]]
}

class BasicOauthCode[RO <: OauthResourceOwner, C <: OauthClient]
                    (val value: String,
                     val owner:RO,
                     val client: C,
                     val issueAt: Long,
                     val expireIn: Long = OAuth.MaximumLifetime.toMillis,
                     val revoked: Boolean = false,
                     val redirectUri: Option[String] = None,
                     val scopes: Option[Seq[String]] = None) extends OauthCode[RO, C]
