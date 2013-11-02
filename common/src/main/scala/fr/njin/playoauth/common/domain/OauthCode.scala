package fr.njin.playoauth.common.domain

import fr.njin.playoauth.common.OAuth
import scala.concurrent.Future

trait OauthCode[RO <: OauthResourceOwner, P <: OauthPermission[C], C <: OauthClient] {
  def value: String
  def owner: RO
  def client:C
  def issueAt: Long
  def expireIn: Long
  def revoked: Boolean
  def redirectUri: Option[String]
  def scopes: Option[Seq[String]]
}

trait OauthCodeFactory[CO <: OauthCode[RO, P, C], RO <: OauthResourceOwner, P <: OauthPermission[C], C <: OauthClient] {
  def apply(owner:RO, client:C, redirectUri: Option[String], scopes: Option[Seq[String]]): Future[CO]
}

trait OauthCodeRepository[CO <: OauthCode[RO, P, C], RO <: OauthResourceOwner, P <: OauthPermission[C], C <: OauthClient] {
  def find(value: String): Future[Option[CO]]
  def revoke(value: String): Future[Option[CO]]
}

class BasicOauthCode[RO <: OauthResourceOwner, P <: OauthPermission[C], C <: OauthClient]
                    (val value: String,
                     val owner:RO,
                     val client: C,
                     val issueAt: Long,
                     val expireIn: Long = OAuth.MaximumLifetime.toMillis,
                     val revoked: Boolean = false,
                     val redirectUri: Option[String] = None,
                     val scopes: Option[Seq[String]] = None) extends OauthCode[RO, P, C]