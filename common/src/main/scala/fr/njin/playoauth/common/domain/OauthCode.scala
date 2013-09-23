package fr.njin.playoauth.common.domain

import fr.njin.playoauth.common.OAuth
import scala.concurrent.{ExecutionContext, Future}

trait OauthCode[RO <: OauthResourceOwner[C, P], P <: OauthPermission[C], C <: OauthClient] {
  def value: String
  def owner: RO
  def client:C
  def issueAt: Long
  def expireIn: Long
  def revokedAt: Option[Long]
  def redirectUri: Option[String]
  def scopes: Option[Seq[String]]
}

trait OauthCodeFactory[CO <: OauthCode[RO, P, C], RO <: OauthResourceOwner[C, P], P <: OauthPermission[C], C <: OauthClient] {
  def apply(owner:RO, client:C, redirectUri: Option[String], scopes: Option[Seq[String]])(implicit ec:ExecutionContext): Future[CO]
}

trait OauthCodeRepository[CO <: OauthCode[RO, P, C], RO <: OauthResourceOwner[C, P], P <: OauthPermission[C], C <: OauthClient] {

  def save(code: CO)(implicit ec:ExecutionContext): Future[CO]
  def find(value: String)(implicit ec:ExecutionContext): Future[Option[CO]]

}

class BasicOauthCode[RO <: OauthResourceOwner[C, P], P <: OauthPermission[C], C <: OauthClient]
                    (val value: String,
                     val owner:RO,
                     val client: C,
                     val issueAt: Long,
                     val expireIn: Long = OAuth.MaximumLifetime.toMillis,
                     val revokedAt: Option[Long] = None,
                     val redirectUri: Option[String] = None,
                     val scopes: Option[Seq[String]] = None) extends OauthCode[RO, P, C]
