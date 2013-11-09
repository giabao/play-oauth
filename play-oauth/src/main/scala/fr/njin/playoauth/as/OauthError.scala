package fr.njin.playoauth.as

import fr.njin.playoauth.common.OAuth
import play.api.libs.json._
import play.api.libs.functional.syntax._
import scala.language.implicitConversions

case class OauthError(error:String,
                      errorDescription:Option[String] = None,
                      errorUri:Option[String] = None)

object OauthError {

  implicit val writes: Writes[OauthError] = (
    (__ \ OAuth.OauthError).write[String] ~
    (__ \ OAuth.OauthErrorDescription).writeNullable[String] ~
    (__ \ OAuth.OauthErrorUri).writeNullable[String]
  )(unlift(OauthError.unapply))

  implicit def toQuery(error: OauthError):Map[String, Seq[String]] =
    Map(OAuth.OauthError -> Seq(error.error)) ++
                            error.errorDescription.map(OAuth.OauthErrorDescription -> Seq(_)) ++
                            error.errorUri.map(OAuth.OauthErrorUri -> Seq(_))

  def invalidRequestError(errorDescription:Option[String] = None, errorUri:Option[String] = None): OauthError =
    OauthError(OAuth.ErrorCode.InvalidRequest, errorDescription, errorUri)

  def invalidClientError(errorDescription:Option[String] = None, errorUri:Option[String] = None): OauthError =
    OauthError(OAuth.ErrorCode.InvalidClient, errorDescription, errorUri)

  def invalidGrantError(errorDescription:Option[String] = None, errorUri:Option[String] = None): OauthError =
    OauthError(OAuth.ErrorCode.InvalidGrant, errorDescription, errorUri)

  def unauthorizedClientError(errorDescription:Option[String] = None, errorUri:Option[String] = None): OauthError =
    OauthError(OAuth.ErrorCode.UnauthorizedClient, errorDescription, errorUri)

  def unsupportedGrantTypeError(errorDescription:Option[String] = None, errorUri:Option[String] = None): OauthError =
    OauthError(OAuth.ErrorCode.UnsupportedGrantType, errorDescription, errorUri)

  def invalidScopeError(errorDescription:Option[String] = None, errorUri:Option[String] = None): OauthError =
    OauthError(OAuth.ErrorCode.InvalidScope, errorDescription, errorUri)

  def accessDeniedError(errorDescription:Option[String] = None, errorUri:Option[String] = None): OauthError =
    OauthError(OAuth.ErrorCode.AccessDenied, errorDescription, errorUri)

  def unsupportedResponseTypeError(errorDescription:Option[String] = None, errorUri:Option[String] = None): OauthError =
    OauthError(OAuth.ErrorCode.UnsupportedResponseType, errorDescription, errorUri)

  def serverError(errorDescription:Option[String] = None, errorUri:Option[String] = None): OauthError =
    OauthError(OAuth.ErrorCode.ServerError, errorDescription, errorUri)
}
