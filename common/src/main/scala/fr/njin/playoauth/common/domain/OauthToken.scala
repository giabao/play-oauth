package fr.njin.playoauth.common.domain

import scala.concurrent.Future
import java.util.Date

/**
 * Represents the oauth2 token
 *
 * @tparam RO Type of the resource owner
 * @tparam C Type of the client
 */
trait OauthToken[RO <: OauthResourceOwner, C <: OauthClient] {

  /**
   * @return the resource owner of the token
   */
  def owner: RO

  /**
   * @return the client of the token
   */
  def client: C

  /**
   * @return the value of the token
   */
  def accessToken: String

  /**
   * @return the type of the token. Ex: Bearer
   */
  def tokenType: String

  /**
   * @return true if the token is revoked
   *
   * The authorization endpoint revoke a token just before
   * issuing a new one from the refresh token value
   */
  def revoked: Boolean

  /**
   * @return the creation timestamp of the code
   */
  def issueAt: Long

  /**
   * @return the life time of the token in milliseconds
   */
  def expiresIn: Option[Long]

  /**
   * @return the refresh token value of the token
   */
  def refreshToken: Option[String]

  /**
   * @return the scope of the token
   */
  def scopes: Option[Seq[String]]

  def hasExpired: Boolean = {
    expiresIn.exists(_ + issueAt < new Date().getTime)
  }

}

trait OauthTokenFactory[TO <: OauthToken[RO, C], RO <: OauthResourceOwner, C <: OauthClient] {
  def apply(owner:RO, client: C, redirectUri: Option[String], scopes: Option[Seq[String]]): Future[TO]
}

trait OauthTokenRepository[TO <: OauthToken[RO, C], RO <: OauthResourceOwner, C <: OauthClient] {

  def find(value: String): Future[Option[TO]]
  def findForRefreshToken(value: String): Future[Option[TO]]
  def revoke(value: String): Future[Option[TO]]

}

class BasicOauthToken[RO <: OauthResourceOwner, C <: OauthClient](
                      val owner: RO,
                      val client: C,
                      val accessToken: String,
                      val tokenType: String,
                      val issueAt: Long = new Date().getTime,
                      val revoked: Boolean = false,
                      val expiresIn: Option[Long] = None,
                      val refreshToken: Option[String] = None,
                      val scopes: Option[Seq[String]] = None) extends OauthToken[RO, C]
