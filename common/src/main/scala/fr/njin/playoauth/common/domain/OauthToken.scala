package fr.njin.playoauth.common.domain

import scala.concurrent.{Future, ExecutionContext}
import play.api.libs.json._
import fr.njin.playoauth.common.OAuth
import play.api.libs.functional.syntax._

trait OauthToken {

  def accessToken: String
  def tokenType: String
  def expiresIn: Option[Long]
  def refreshToken: Option[String]
  def scope: Option[Seq[String]]

}

trait OauthTokenFactory[TO <: OauthToken, CO <: OauthCode[RO, P, C], RO <: OauthResourceOwner[C, P], P <: OauthPermission[C], C <: OauthClient] {
  def apply(code:CO, redirectUri: Option[String])(implicit ec:ExecutionContext): Future[TO]
}

trait OauthTokenRepository[TO <: OauthToken] {

  def save(token: TO)(implicit ec:ExecutionContext): Future[TO]
  def find(value: String)(implicit ec:ExecutionContext): Future[Option[TO]]

}

class BasicOauthToken(val accessToken: String,
                      val tokenType: String,
                      val expiresIn: Option[Long] = None,
                      val refreshToken: Option[String] = None,
                      val scope: Option[Seq[String]] = None) extends OauthToken


object BasicOauthToken {
  implicit val writes: Writes[BasicOauthToken] = (
    (__ \ OAuth.OauthAccessToken).write[String] ~
    (__ \ OAuth.OauthTokenType).write[String] ~
    (__ \ OAuth.OauthExpiresIn).writeNullable[Long] ~
    (__ \ OAuth.OauthRefreshToken).writeNullable[String] ~
    (__ \ OAuth.OauthScope).writeNullable[String]
  )(t => (t.accessToken, t.tokenType, t.expiresIn, t.refreshToken, t.scope.map(_.mkString(" "))))

}