package fr.njin.playoauth.common.domain

import scala.concurrent.Future

trait OauthToken[RO <: OauthResourceOwner, P <: OauthPermission[C], C <: OauthClient] {

  def owner: RO
  def client: C

  def accessToken: String
  def tokenType: String
  def revoked: Boolean
  def expiresIn: Option[Long]
  def refreshToken: Option[String]
  def scope: Option[Seq[String]]

}

trait OauthTokenFactory[TO <: OauthToken[RO, P, C], RO <: OauthResourceOwner, P <: OauthPermission[C], C <: OauthClient] {
  def apply(owner:RO, client: C, redirectUri: Option[String], scopes: Option[Seq[String]]): Future[TO]
}

trait OauthTokenRepository[TO <: OauthToken[RO, P, C], RO <: OauthResourceOwner, P <: OauthPermission[C], C <: OauthClient] {

  def find(value: String): Future[Option[TO]]
  def findForRefreshToken(value: String): Future[Option[TO]]
  def revoke(value: String): Future[Option[TO]]

}

class BasicOauthToken[RO <: OauthResourceOwner, P <: OauthPermission[C], C <: OauthClient](
                      val owner: RO,
                      val client: C,
                      val accessToken: String,
                      val tokenType: String,
                      val revoked: Boolean = false,
                      val expiresIn: Option[Long] = None,
                      val refreshToken: Option[String] = None,
                      val scope: Option[Seq[String]] = None) extends OauthToken[RO, P, C]