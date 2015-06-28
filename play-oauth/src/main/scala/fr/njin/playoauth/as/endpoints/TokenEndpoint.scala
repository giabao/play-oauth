package fr.njin.playoauth.as.endpoints

import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future}
import play.api.data.Form
import fr.njin.playoauth.common.OAuth
import play.api.i18n.{I18nSupport, Messages}
import fr.njin.playoauth.common.domain._
import Results._
import fr.njin.playoauth.as.OauthError
import OauthError._
import play.api.libs.json.{JsValue, Writes, Json}
import fr.njin.playoauth.common.request._
import Requests._
import fr.njin.playoauth.common.request.PasswordTokenRequest
import fr.njin.playoauth.common.request.AuthorizationCodeTokenRequest
import fr.njin.playoauth.common.request.ClientCredentialsTokenRequest
import fr.njin.playoauth.as.OauthError
import play.api.Logger

/**
 * User: bathily
 * Date: 17/09/13
 */

trait ClientAuthentication[C <: OauthClient] {
  def authenticate(request: Request[AnyContentAsFormUrlEncoded]): Future[Either[Option[C], OauthError]]
}

trait SecretKeyClientAuthentication[C <: OauthClient] extends ClientAuthentication[C] {

  def authenticate(request: Request[AnyContentAsFormUrlEncoded]): Future[Either[Option[C], OauthError]] = {
    val data = request.body.data
    (for {
      id <- data.get(OAuth.OauthClientId).flatMap(_.headOption)
      secret <- data.get(OAuth.OauthClientSecret).flatMap(_.headOption)
    } yield (id, secret))
      .map { case (id, secret) =>
        authenticate(id, secret)
      }.getOrElse(Future.successful(Left(None)))
  }

  def authenticate(id: String, secret: String): Future[Either[Option[C], OauthError]]

}

trait Token[C <: OauthClient, CO <: OauthCode, RO <: OauthResourceOwner, TO <: OauthToken] extends I18nSupport {

  this: ClientAuthentication[C] =>

  val logger:Logger = TokenEndpoint.logger

  def clientRepository: OauthClientRepository[C]
  def codeRepository: OauthCodeRepository[CO]
  def tokenFactory: OauthTokenFactory[TO]
  def tokenRepository: OauthTokenRepository[TO]
  def supportedGrantType: Seq[String]

  type TokenValidation = (TokenRequest, C) => (ExecutionContext, Messages) => Future[Option[OauthError]]
  type CodeValidation = (TokenRequest, C, CO) => (ExecutionContext, Messages) => Future[Option[OauthError]]

  val clientGrantTypeValidation:TokenValidation = (tokenRequest, client) => (ec, messages) => Future.successful {
    if(client.allowedGrantType.contains(tokenRequest.grantType)) {
      None
    } else {
      val desc = messages(OAuth.ErrorUnauthorizedGrantType, tokenRequest.grantType)
      Some(unauthorizedClientError(Some(desc)))
    }
  }

  val tokenValidator = List(clientGrantTypeValidation)

  val codeClientValidation:CodeValidation = (tokenRequest, client, code) => (ec, messages) => Future.successful {
    if(code.clientId == client.id) {
      None
    } else {
      Some(invalidGrantError(Some(messages(OAuth.ErrorClientNotMatch))))
    }
  }

  val codeExpireValidation:CodeValidation = (tokenRequest, client, code) => (ec, messages) => Future.successful {
    if(!code.hasExpired) None
    else Some(invalidGrantError(Some(messages(OAuth.ErrorExpiredAuthorizationCode))))
  }

  val codeRevokeValidation:CodeValidation = (tokenRequest, client, code) => (ec, messages) => Future.successful {
    if(code.revoked) {
      Some(invalidGrantError(Some(messages(OAuth.ErrorRevokedAuthorizationCode))))
    } else {
      None
    }
  }

  val codeRedirectUriValidation:CodeValidation = (tokenRequest, client, code) => (ec, messages) => Future.successful {
    tokenRequest match {
      case AuthorizationCodeTokenRequest(_, _, uri) =>
        code.redirectUri == uri match {
          case false if uri.isEmpty => Some(invalidRequestError(Some(OAuth.ErrorRedirectURIMissing)))
          case false => Some(invalidGrantError(Some(messages(OAuth.ErrorRedirectURINotMatch))))
          case _ => None
        }
      case _ => None
    }
  }

  val codeValidator = List(codeClientValidation, codeExpireValidation, codeRevokeValidation, codeRedirectUriValidation)

  def errorToJson(error: OauthError)(implicit writes: Writes[OauthError]):JsValue = Json.toJson(error)(writes)

  def clientOf(tokenRequest: TokenRequest)
              (implicit request: Request[AnyContentAsFormUrlEncoded], ec:ExecutionContext): Future[Either[Option[C], OauthError]] =
    tokenRequest match {
      case AuthorizationCodeTokenRequest(_, clientId, _) => clientRepository.find(clientId).map(Left(_))
      case _ => authenticate(request)
    }

  def onTokenFormError(f:Form[_ <: TokenRequest])
                      (implicit request:Request[AnyContentAsFormUrlEncoded],
                                ec:ExecutionContext,
                                writes: Writes[OauthError]): Future[Result] = {
    Future.successful(BadRequest(errorToJson(invalidRequestError(Some(f.errorsAsJson.toString())))))
  }

  def onTokenRequest(tokenRequest: TokenRequest)
                    (f:(TokenRequest, C) => Request[AnyContentAsFormUrlEncoded] => Future[Result])
                    (implicit request:Request[AnyContentAsFormUrlEncoded],
                              ec:ExecutionContext,
                              writes: Writes[OauthError]): Future[Result] = {
    clientOf(tokenRequest).flatMap(_.fold(
      _.fold({
        val message = tokenRequest match {
          case AuthorizationCodeTokenRequest(_, clientId, _) => 
            invalidClientError(Some(Messages(OAuth.ErrorClientNotFound, clientId)))
          case _ => 
            invalidClientError()
        }
        Future.successful(Unauthorized(errorToJson(message)))
      }){ client =>
        Future.find(tokenValidator.map(_(tokenRequest, client)(ec, request2Messages(request))))(_.isDefined).flatMap {
          case Some(e) => Future.successful(BadRequest(errorToJson(e.get)))
          case _ => f(tokenRequest, client)(request)
        }
      },
      error => Future.successful(Unauthorized(errorToJson(error)))
    ))
  }

  def token(owner: (String, String) => Future[Option[RO]], //(username, password) => ResourceOwner
            clientOwner: C => Future[Option[RO]])
           (implicit ec:ExecutionContext,
                     writes: Writes[TokenResponse],
                     errorWrites: Writes[OauthError]): Request[AnyContentAsFormUrlEncoded] => Future[Result] =

    token(perform(owner, clientOwner))

  def token(f:(TokenRequest, C) => Request[AnyContentAsFormUrlEncoded] => Future[Result])
           (implicit ec:ExecutionContext,
                     writes: Writes[TokenResponse],
                     errorWrites: Writes[OauthError]): Request[AnyContentAsFormUrlEncoded] => Future[Result] =

    implicit request => {

      val query = request.body.data

      val formOrError: Either[Form[_ <: TokenRequest], OauthError] =
        query.get(OAuth.OauthGrantType)
          .flatMap(_.headOption)
          .fold[Either[Form[_ <: TokenRequest], OauthError]](
            Right(invalidRequestError(Some(Messages(OAuth.ErrorUnsupportedGrantType))))
          ){ grantType =>
            Option(grantType).filter(supportedGrantType.contains)
              .flatMap(tokenForms.get)
              .map(_.bindFromRequest)
              .toLeft(unsupportedGrantTypeError(Some(Messages(OAuth.ErrorUnsupportedGrantType, grantType))))
          }

      formOrError.fold(form => {
        Option(query.filter(_._2.length > 1)).filterNot(_.isEmpty).map { params =>
          form.withGlobalError(Messages(OAuth.ErrorMultipleParameters, params.keySet.mkString(",")))
        }.getOrElse(form).fold(onTokenFormError, onTokenRequest(_)(f))
      }, error => {
        Future.successful(BadRequest(errorToJson(error)))
      })

    }

  def perform(owner: (String, String) => Future[Option[RO]], //(username, password) => ResourceOwner
              clientOwner: C => Future[Option[RO]])
             (implicit ec:ExecutionContext,
                       writes: Writes[TokenResponse],
                       errorWrites: Writes[OauthError]): (TokenRequest, C) => Request[AnyContentAsFormUrlEncoded] => Future[Result] =

    (tokenRequest, oauthClient) => request => {
      tokenRequest match {
        case t:AuthorizationCodeTokenRequest =>
          codeRepository.find(t.code).flatMap(_.fold(
            Future.successful(BadRequest(errorToJson(invalidGrantError(Some(Messages(OAuth.ErrorUnknownAuthorizationCode, t.code))))))
          ){ code =>
            Future.find(codeValidator.map(_(tokenRequest, oauthClient, code)(ec, request2Messages(request))))(_.isDefined).flatMap {
              case Some(e) => Future.successful(BadRequest(errorToJson(e.get)))
              case _ => for {
                consumed <- codeRepository.revoke(t.code)
                token <- issueAToken(code.ownerId, code.clientId, t.redirectUri, code.scopes)
              } yield token
            }
          })

        case t:PasswordTokenRequest =>
          owner(t.username, t.password).flatMap(_.fold(
            Future.successful(BadRequest(errorToJson(invalidGrantError(Some(Messages(OAuth.ErrorInvalidCredentials))))))
          ){ resourceOwner =>
             issueAToken(resourceOwner.id, oauthClient.id, None, t.scope)
          })

        case t:ClientCredentialsTokenRequest =>
          clientOwner(oauthClient).flatMap(_.fold(
            Future.successful(BadRequest(errorToJson(invalidGrantError(Some(Messages(OAuth.ErrorInvalidCredentials))))))
          ){ resourceOwner =>
            issueAToken(resourceOwner.id, oauthClient.id, None, t.scope)
          })

        case t:RefreshTokenRequest =>
          tokenRepository.findForRefreshToken(t.refreshToken).flatMap(_.fold(
            Future.successful(BadRequest(errorToJson(invalidGrantError(Some(Messages(OAuth.ErrorClientNotFound, oauthClient.id))))))
          ){ previousToken =>
            for {
              Some(revoked) <- tokenRepository.revoke(previousToken.accessToken)
              token <- issueAToken(revoked.ownerId, revoked.clientId, None, revoked.scopes)
            } yield token
          })

        //Can not happen
        case _ => Future.successful(BadRequest(errorToJson(unsupportedGrantTypeError(Some(Messages(OAuth.ErrorUnsupportedGrantType))))))
      }
    }

  def issueAToken(ownerId: String, clientId: String, redirectUri: Option[String], scope: Option[Seq[String]])
                 (implicit ec:ExecutionContext, writes: Writes[TokenResponse]): Future[Result] =
    tokenFactory(ownerId, clientId, redirectUri, scope).map { token =>
      Ok(Json.toJson(TokenResponse(token)))
        .withHeaders("Cache-Control" -> "no-store", "Pragma" -> "no-cache")
    }

  def info(token: String)
          (authenticate: RequestHeader => Future[Option[C]])
          (ok: TO => Future[Result])
          (onUnauthorized: Future[Result] = Future.successful(Unauthorized("")),
           onTokenNotFound: Future[Result] = Future.successful(NotFound("")),
           onForbidden: Future[Result] = Future.successful(Forbidden("")))
          (implicit ec: ExecutionContext): RequestHeader => Future[Result] = request => {

    authenticate(request).flatMap {
      _.fold(onUnauthorized) { client =>
        tokenRepository.find(token).flatMap {
          _.fold(onTokenNotFound) { token =>
            if(token.clientId == client.id) {
              ok(token)
            } else {
              onForbidden
            }
          }
        }
      }
    }

  }
}

abstract class TokenEndpoint[C <: OauthClient, CO <: OauthCode, RO <: OauthResourceOwner, TO <: OauthToken](
  val clientRepository: OauthClientRepository[C],
  val codeRepository: OauthCodeRepository[CO],
  val tokenFactory: OauthTokenFactory[TO],
  val tokenRepository: OauthTokenRepository[TO],
  val supportedGrantType: Seq[String] = OAuth.GrantType.All
) extends Token[C, CO, RO, TO] {
  this: ClientAuthentication[C] =>
}

object TokenEndpoint {
  val logger:Logger = Logger(getClass)
}
