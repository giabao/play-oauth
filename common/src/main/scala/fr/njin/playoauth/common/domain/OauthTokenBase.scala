package fr.njin.playoauth.common.domain

import java.time.Instant
import scala.concurrent.duration.FiniteDuration
import com.sandinh.util.TimeUtil._

/** base trait for OauthCode & OauthToken */
trait OauthTokenBase {
  /**
   * @return the value of the code. Must be unique.
   */
  def value: String

  /**
   * @return the resource owner of the code / token.
   * @see [[fr.njin.playoauth.common.domain.OauthResourceOwner]]
   */
  def ownerId: String

  /**
   * @return the client of the code/ token
   * @see [[fr.njin.playoauth.common.domain.OauthClient]]
   */
  def clientId:String

  /**
   * @return the creation timestamp of the code/ token
   */
  def issueAt: Instant

  /**
   * @return the life time of the code/ token
   */
  def expiresIn: FiniteDuration

  /**
   * @return the scope of the code/ token
   */
  def scopes: Option[Seq[String]]

  /**
   * @return true if the code/ token is revoked
   *
   * The code is revoked by the authorization endpoint before issuing a token.
   *
   * The authorization endpoint revoke a token just before
   * issuing a new one from the refresh token value
   */
  def revoked: Boolean

  def hasExpired: Boolean = issueAt + expiresIn < Instant.now
}
