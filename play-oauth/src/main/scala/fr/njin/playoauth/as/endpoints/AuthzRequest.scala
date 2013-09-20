package fr.njin.playoauth.as.endpoints

import play.api.data.Forms._
import play.api.data.{FormError, Form}
import fr.njin.playoauth.common.OAuth
import Constraints._
import play.api.data.format.Formatter

/**
 * User: bathily
 * Date: 17/09/13
 */
case class AuthzRequest(responseType: String, clientId: String, redirectUri: Option[String], scope: Option[Seq[String]], state: Option[String])

object AuthzRequest {
  val authorizeRequestForm = Form (
    mapping(
      OAuth.OauthResponseType -> nonEmptyText,
      OAuth.OauthClientId -> nonEmptyText,
      OAuth.OauthRedirectUri -> optional(text.verifying(uri)),
      OAuth.OauthScope -> optional(of[Seq[String]](new Formatter[Seq[String]] {
        def unbind(key: String, value: Seq[String]): Map[String, String] = Map((key, value.mkString(" ")))
        def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Seq[String]] =
          data.get(key).fold[Either[Seq[FormError], Seq[String]]](Left(Seq(FormError(key, "error.required", Nil))))(v => Right(v.split(" ").toSeq))
      })),
      OAuth.OauthState -> optional(text)
    )(AuthzRequest.apply)(AuthzRequest.unapply)
  )
}
