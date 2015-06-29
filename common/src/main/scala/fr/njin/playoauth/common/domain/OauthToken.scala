package fr.njin.playoauth.common.domain

import java.time.Instant
import fr.njin.playoauth.common.OAuth
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
 * Represents the oauth2 token
 */
trait OauthToken extends OauthTokenBase {
  /**
   * @return the value of the token
   */
  def accessToken: String = value

  /**
   * @return the type of the token. Ex: Bearer
   */
  def tokenType: String

  /**
   * @return the refresh token value of the token
   */
  def refreshToken: Option[String]
}

trait OauthTokenFactory[TO <: OauthToken] {
  def apply(ownerId:String, clientId: String, redirectUri: Option[String], scopes: Option[Seq[String]]): Future[TO]
}

trait OauthTokenRepository[TO <: OauthToken] {
  def find(value: String): Future[Option[TO]]
  /** find OauthToken by refreshToken and revoke the accessToken  */
  def revokeByRefreshToken(refreshToken: String): Future[Option[TO]]
  /** revoke by accessToken */
  def revoke(value: String): Future[Option[TO]]
}

class BasicOauthToken(val value: String,
                      val ownerId: String,
                      val clientId: String,
                      val tokenType: String,
                      val issueAt: Instant = Instant.now,
                      val expiresIn: FiniteDuration = OAuth.MaximumLifetime,
                      val revoked: Boolean = false,
                      val refreshToken: Option[String] = None,
                      val scopes: Option[Seq[String]] = None) extends OauthToken
