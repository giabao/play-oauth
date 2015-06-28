package fr.njin.playoauth.common.domain

import scala.concurrent.Future
import java.util.Date

/**
 * Represents the oauth2 token
 */
trait OauthToken {

  /**
   * @return the resource owner of the token
   */
  def ownerId: String

  /**
   * @return the client of the token
   */
  def clientId: String

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

trait OauthTokenFactory[TO <: OauthToken] {
  def apply(ownerId:String, clientId: String, redirectUri: Option[String], scopes: Option[Seq[String]]): Future[TO]
}

trait OauthTokenRepository[TO <: OauthToken] {

  def find(value: String): Future[Option[TO]]
  def findForRefreshToken(value: String): Future[Option[TO]]
  def revoke(value: String): Future[Option[TO]]

}

class BasicOauthToken(val ownerId: String,
                      val clientId: String,
                      val accessToken: String,
                      val tokenType: String,
                      val issueAt: Long = new Date().getTime,
                      val revoked: Boolean = false,
                      val expiresIn: Option[Long] = None,
                      val refreshToken: Option[String] = None,
                      val scopes: Option[Seq[String]] = None) extends OauthToken
