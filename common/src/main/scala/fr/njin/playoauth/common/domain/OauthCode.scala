package fr.njin.playoauth.common.domain

import java.time.Instant
import fr.njin.playoauth.common.OAuth
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
 * Represents the oauth2 code issuing by the authorization server
 *
 * An oauth2 code is issuing for a resource owner and a client. Its value must be unique
 */
trait OauthCode extends OauthTokenBase {
  /**
   * @return the redirectUri specified by the client when requesting the code
   */
  def redirectUri: Option[String]
}

/**
 * Factory used to create a code
 *
 * @tparam CO Type of the code
 */
trait OauthCodeFactory[CO <: OauthCode] {
  def apply(ownerId:String, clientId:String, redirectUri: Option[String], scopes: Option[Seq[String]]): Future[CO]
}

/**
 * Repository used to retrieve a code by its value and to revoke a code
 *
 * @tparam CO Type of the code
 */
trait OauthCodeRepository[CO <: OauthCode] {
  def find(value: String): Future[Option[CO]]
  def revoke(value: String): Future[Option[CO]]
}

class BasicOauthCode(val value: String,
                     val ownerId:String,
                     val clientId: String,
                     val issueAt: Instant = Instant.now,
                     val expiresIn: FiniteDuration = OAuth.MaximumLifetime,
                     val revoked: Boolean = false,
                     val redirectUri: Option[String] = None,
                     val scopes: Option[Seq[String]] = None) extends OauthCode
