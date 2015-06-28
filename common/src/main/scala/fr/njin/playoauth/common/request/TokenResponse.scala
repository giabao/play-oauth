package fr.njin.playoauth.common.request

import play.api.libs.json._
import play.api.libs.functional.syntax._
import fr.njin.playoauth.common.OAuth
import fr.njin.playoauth.common.domain.OauthToken

/**
 * The response to a token request
 *
 * @param accessToken value of the token
 * @param tokenType type of the token. Ex : Bearer
 * @param expiresIn life time of the token in seconds
 * @param refreshToken value of the eventual refresh token
 * @param scopes scopes of the token
 */
case class TokenResponse(accessToken: String,
                         tokenType: String,
                         expiresIn: Long,
                         refreshToken: Option[String] = None,
                         scopes: Option[Seq[String]] = None)

/**
 * Factory of [[fr.njin.playoauth.common.request.TokenResponse]] and implicit json writer
 */
object TokenResponse {

  /**
   * Create a token response from the oauth2 token
   * @param t The token
   * @return The response
   */
  def apply(t:OauthToken): TokenResponse = TokenResponse(t.accessToken, t.tokenType, t.expiresIn.toSeconds, t.refreshToken, t.scopes)

  /**
   * Convert the response token to a json
   */
  implicit val tokenWrites: Writes[TokenResponse] = (
    (__ \ OAuth.OauthAccessToken).write[String] ~
      (__ \ OAuth.OauthTokenType).write[String] ~
      (__ \ OAuth.OauthExpiresIn).write[Long] ~
      (__ \ OAuth.OauthRefreshToken).writeNullable[String] ~
      (__ \ OAuth.OauthScope).writeNullable[String]
    )(t => (t.accessToken, t.tokenType, t.expiresIn, t.refreshToken, t.scopes.map(_.mkString(" "))))

}
