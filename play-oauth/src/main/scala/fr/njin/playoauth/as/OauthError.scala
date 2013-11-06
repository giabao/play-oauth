package fr.njin.playoauth.as

import fr.njin.playoauth.common.OAuth
import play.api.libs.json._
import play.api.libs.functional.syntax._
import scala.language.implicitConversions

case class OauthError(error:String, errorDescription:Option[String] = None, errorUri:Option[String] = None)

object OauthError {

  implicit val writes: Writes[OauthError] = (
    (__ \ OAuth.OauthError).write[String] ~
    (__ \ OAuth.OauthErrorDescription).writeNullable[String] ~
    (__ \ OAuth.OauthErrorUri).writeNullable[String]
  )(unlift(OauthError.unapply))

  implicit def toQuery(error: OauthError) = Map(OAuth.OauthError -> Seq(error.error)) ++ error.errorDescription.map(OAuth.OauthErrorDescription -> Seq(_)) ++ error.errorUri.map(OAuth.OauthErrorUri -> Seq(_))

  def InvalidRequestError(errorDescription:Option[String] = None, errorUri:Option[String] = None) = OauthError(OAuth.ErrorCode.InvalidRequest, errorDescription, errorUri)
  def InvalidClientError(errorDescription:Option[String] = None, errorUri:Option[String] = None) = OauthError(OAuth.ErrorCode.InvalidClient, errorDescription, errorUri)
  def InvalidGrantError(errorDescription:Option[String] = None, errorUri:Option[String] = None) = OauthError(OAuth.ErrorCode.InvalidGrant, errorDescription, errorUri)
  def UnauthorizedClientError(errorDescription:Option[String] = None, errorUri:Option[String] = None) = OauthError(OAuth.ErrorCode.UnauthorizedClient, errorDescription, errorUri)
  def UnsupportedGrantTypeError(errorDescription:Option[String] = None, errorUri:Option[String] = None) = OauthError(OAuth.ErrorCode.UnsupportedGrantType, errorDescription, errorUri)
  def InvalidScopeError(errorDescription:Option[String] = None, errorUri:Option[String] = None) = OauthError(OAuth.ErrorCode.InvalidScope, errorDescription, errorUri)
  def AccessDeniedError(errorDescription:Option[String] = None, errorUri:Option[String] = None) = OauthError(OAuth.ErrorCode.AccessDenied, errorDescription, errorUri)
  def UnsupportedResponseTypeError(errorDescription:Option[String] = None, errorUri:Option[String] = None) = OauthError(OAuth.ErrorCode.UnsupportedResponseType, errorDescription, errorUri)
  def ServerError(errorDescription:Option[String] = None, errorUri:Option[String] = None) = OauthError(OAuth.ErrorCode.ServerError, errorDescription, errorUri)
  
}
