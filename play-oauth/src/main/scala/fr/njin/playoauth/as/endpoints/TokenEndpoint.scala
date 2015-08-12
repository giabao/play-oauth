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

trait Token[Client <: OauthClient, Code <: OauthCode, Owner <: OauthResourceOwner, OAToken <: OauthToken] extends I18nSupport {

  this: ClientAuthentication[Client] =>

  val logger:Logger = TokenEndpoint.logger

  def clientRepository: OauthClientRepository[Client]
  def codeRepository: OauthCodeRepository[Code]
  def tokenFactory: OauthTokenFactory[OAToken]
  def tokenRepository: OauthTokenRepository[OAToken]
  def supportedGrantType: Seq[String]

  type TokenValidation = (TokenRequest, Client) => (ExecutionContext, Messages) => Future[Option[OauthError]]
  type CodeValidation = (TokenRequest, Client, Code) => (ExecutionContext, Messages) => Future[Option[OauthError]]

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
              (implicit request: Request[AnyContentAsFormUrlEncoded], ec:ExecutionContext): Future[Either[Option[Client], OauthError]] =
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
                    (f:(TokenRequest, Client) => Request[AnyContentAsFormUrlEncoded] => Future[Result])
                    (implicit request:Request[AnyContentAsFormUrlEncoded],
                              ec:ExecutionContext,
                              writes: Writes[OauthError]): Future[Result] = {
    clientOf(tokenRequest).flatMap(_.fold(
      _.fold {
        Future.successful(Unauthorized(errorToJson(mapToError(tokenRequest))))
      } {
        client =>
          Future.find(validateToken(tokenRequest, request, ec, client))(_.isDefined).flatMap {
            case Some(e) => Future.successful(BadRequest(errorToJson(e.get)))
            case _ => f(tokenRequest, client)(request)
          }
      },
      error => Future.successful(Unauthorized(errorToJson(error)))
    ))
  }

  def validateToken(tokenRequest: TokenRequest, request: Request[AnyContentAsFormUrlEncoded], ec: ExecutionContext, client: Client): List[Future[Option[OauthError]]] = {
    tokenValidator.map(_(tokenRequest, client)(ec, request2Messages(request)))
  }

  def mapToError(tokenRequest: TokenRequest): OauthError = {
    tokenRequest match {
      case AuthorizationCodeTokenRequest(_, clientId, _) =>
        invalidClientError(Some(Messages(OAuth.ErrorClientNotFound, clientId)))
      case _ =>
        invalidClientError()
    }
  }

  def token(owner: (String, String) => Future[Option[Owner]], //(username, password) => ResourceOwner
            toOwner: Client => Future[Option[Owner]])
           (implicit ec:ExecutionContext,
                     writes: Writes[TokenResponse],
                     errorWrites: Writes[OauthError]): Request[AnyContentAsFormUrlEncoded] => Future[Result] =

    token(perform(owner, toOwner))

  def token(f:(TokenRequest, Client) => Request[AnyContentAsFormUrlEncoded] => Future[Result])
           (implicit ec:ExecutionContext,
                     writes: Writes[TokenResponse],
                     errorWrites: Writes[OauthError]):
    Request[AnyContentAsFormUrlEncoded] => Future[Result] =

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
        Option(query.filter(_._2.length > 1)).
          filterNot(_.isEmpty).map { params =>
            form.withGlobalError(
              Messages(OAuth.ErrorMultipleParameters, params.keySet.mkString(",")))
        }.getOrElse(form).
          fold(onTokenFormError, onTokenRequest(_)(f))
      }, error => {
        Future.successful(BadRequest(errorToJson(error)))
      })

    }

  def perform(owner: (String, String) => Future[Option[Owner]], //(username, password) => ResourceOwner
              toOwnerFunc: Client => Future[Option[Owner]])
             (implicit ec:ExecutionContext,
                       writes: Writes[TokenResponse],
                       errorWrites: Writes[OauthError]):
    (TokenRequest, Client) => Request[AnyContentAsFormUrlEncoded] => Future[Result] =

    (t, client) => request => t match {
      case t: AuthorizationCodeTokenRequest => println("auth"); authorizationCode(t, client, request)
      case t: PasswordTokenRequest => println("password"); passwordToken(t, client, owner)
      case t: ClientCredentialsTokenRequest => println("client"); clientCredentialToken(t, client, toOwnerFunc)
      case t: RefreshTokenRequest => println("refresh"); refreshToken(t, client)

      //Can not happen
      case _ => Future.successful(BadRequest(errorToJson(unsupportedGrantTypeError(Some(Messages(OAuth.ErrorUnsupportedGrantType))))))
    }

  def authorizationCode(r: AuthorizationCodeTokenRequest, oauthClient: Client, request: Request[AnyContentAsFormUrlEncoded])(implicit ec: ExecutionContext): Future[Result] = {
    codeRepository.find(r.code).flatMap(_.fold(
      Future.successful(BadRequest(errorToJson(invalidGrantError(Some(Messages(OAuth.ErrorUnknownAuthorizationCode, r.code))))))
    ) { code =>
      Future.find(codeValidator.map(_(r, oauthClient, code)(ec, request2Messages(request))))(_.isDefined).flatMap {
        case Some(e) => Future.successful(BadRequest(errorToJson(e.get)))
        case _ => for {
          consumed <- codeRepository.revoke(r.code)
          token <- issueAToken(code.ownerId, code.clientId, r.redirectUri, code.scopes)
        } yield token
      }
    })
  }

  def passwordToken(r: PasswordTokenRequest, client: Client, owner: (String, String) => Future[Option[Owner]])(implicit ec: ExecutionContext): Future[Result] = {
    owner(r.username, r.password).flatMap(_.fold(
      Future.successful(BadRequest(errorToJson(invalidGrantError(Some(Messages(OAuth.ErrorInvalidCredentials))))))
    ) { resourceOwner =>
      issueAToken(resourceOwner.id, client.id, None, r.scope)
    })
  }

  def clientCredentialToken(r: ClientCredentialsTokenRequest, oauthClient: Client,
                            owner: (Client) => Future[Option[Owner]])
                           (implicit ec: ExecutionContext): Future[Result] = {
    owner(oauthClient).flatMap(_.fold(
      Future.successful(BadRequest(errorToJson(invalidGrantError(Some(Messages(OAuth.ErrorInvalidCredentials))))))
    ) { resourceOwner =>
      issueAToken(resourceOwner.id, oauthClient.id, None, r.scope)
    })
  }

  def refreshToken(r: RefreshTokenRequest, oauthClient: Client)
                  (implicit ec: ExecutionContext): Future[Result] = {
    tokenRepository.revokeByRefreshToken(r.refreshToken).flatMap(_.fold(
      Future.successful(BadRequest(errorToJson(invalidGrantError(Some(Messages(OAuth.ErrorClientNotFound, oauthClient.id))))))
    ) { revoked =>
      issueAToken(revoked.ownerId, revoked.clientId, None, revoked.scopes)
    })
  }

  def issueAToken(ownerId: String, clientId: String, redirectUri: Option[String], scope: Option[Seq[String]])
                 (implicit ec:ExecutionContext, writes: Writes[TokenResponse]): Future[Result] =
    tokenFactory(ownerId, clientId, redirectUri, scope).map { token =>
      Ok(Json.toJson(TokenResponse(token)))
        .withHeaders("Cache-Control" -> "no-store", "Pragma" -> "no-cache")
    }

  def info(token: String,
           authenticate: RequestHeader => Future[Option[Client]],
           ok: OAToken => Future[Result],
           onUnauthorized: Future[Result] = Future successful Unauthorized,
           onTokenNotFound: Future[Result] = Future successful NotFound,
           onForbidden: Future[Result] = Future successful Forbidden)
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
