package fr.njin.playoauth.common.domain

import scala.concurrent.{Future, ExecutionContext}
import play.api.libs.json._
import fr.njin.playoauth.common.OAuth
import play.api.libs.functional.syntax._

trait OauthToken[RO <: OauthResourceOwner[C, P], P <: OauthPermission[C], C <: OauthClient] {

  def owner: RO
  def client: C

  def accessToken: String
  def tokenType: String
  def revoked: Boolean
  def expiresIn: Option[Long]
  def refreshToken: Option[String]
  def scope: Option[Seq[String]]

}

trait OauthTokenFactory[TO <: OauthToken[RO, P, C], RO <: OauthResourceOwner[C, P], P <: OauthPermission[C], C <: OauthClient] {
  def apply(owner:RO, client: C, redirectUri: Option[String], scopes: Option[Seq[String]])(implicit ec:ExecutionContext): Future[TO]
}

trait OauthTokenRepository[TO <: OauthToken[RO, P, C], RO <: OauthResourceOwner[C, P], P <: OauthPermission[C], C <: OauthClient] {

  def save(token: TO)(implicit ec:ExecutionContext): Future[TO]
  def find(value: String)(implicit ec:ExecutionContext): Future[Option[TO]]
  def findForRefreshToken(value: String)(implicit ec:ExecutionContext): Future[Option[TO]]
  def revoke(value: String)(implicit ec:ExecutionContext): Future[Option[TO]]

}

class BasicOauthToken[RO <: OauthResourceOwner[C, P], P <: OauthPermission[C], C <: OauthClient](
                      val owner: RO,
                      val client: C,
                      val accessToken: String,
                      val tokenType: String,
                      val revoked: Boolean = false,
                      val expiresIn: Option[Long] = None,
                      val refreshToken: Option[String] = None,
                      val scope: Option[Seq[String]] = None) extends OauthToken[RO, P, C]


object BasicOauthToken {
  implicit val writes: Writes[BasicOauthToken[_, _, _]] = (
    (__ \ OAuth.OauthAccessToken).write[String] ~
    (__ \ OAuth.OauthTokenType).write[String] ~
    (__ \ OAuth.OauthExpiresIn).writeNullable[Long] ~
    (__ \ OAuth.OauthRefreshToken).writeNullable[String] ~
    (__ \ OAuth.OauthScope).writeNullable[String]
  )(t => (t.accessToken, t.tokenType, t.expiresIn, t.refreshToken, t.scope.map(_.mkString(" "))))

}