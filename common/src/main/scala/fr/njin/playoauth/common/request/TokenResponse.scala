package fr.njin.playoauth.common.request

import play.api.libs.json._
import play.api.libs.functional.syntax._
import fr.njin.playoauth.common.OAuth
import fr.njin.playoauth.common.domain.OauthToken

case class TokenResponse(accessToken: String,
                         tokenType: String,
                         expiresIn: Option[Long] = None,
                         refreshToken: Option[String] = None,
                         scope: Option[Seq[String]] = None)
object TokenResponse {

  def apply(t:OauthToken[_,_]): TokenResponse = TokenResponse(t.accessToken, t.tokenType, t.expiresIn, t.refreshToken, t.scope)

  implicit val tokenWrites: Writes[TokenResponse] = (
    (__ \ OAuth.OauthAccessToken).write[String] ~
      (__ \ OAuth.OauthTokenType).write[String] ~
      (__ \ OAuth.OauthExpiresIn).writeNullable[Long] ~
      (__ \ OAuth.OauthRefreshToken).writeNullable[String] ~
      (__ \ OAuth.OauthScope).writeNullable[String]
    )(t => (t.accessToken, t.tokenType, t.expiresIn, t.refreshToken, t.scope.map(_.mkString(" "))))

}
