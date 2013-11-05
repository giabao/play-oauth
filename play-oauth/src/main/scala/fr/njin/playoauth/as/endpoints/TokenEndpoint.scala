package fr.njin.playoauth.as.endpoints

import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future}
import play.api.data.Form
import fr.njin.playoauth.common.OAuth
import play.api.i18n.Messages
import fr.njin.playoauth.common.domain._
import scala.Predef._
import Results._
import fr.njin.playoauth.as.OauthError
import OauthError._
import play.api.libs.json.{Writes, Json}
import fr.njin.playoauth.common.request._
import Requests._
import java.util.Date
import fr.njin.playoauth.common.request.PasswordTokenRequest
import fr.njin.playoauth.common.request.AuthorizationCodeTokenRequest
import scala.Some
import play.api.mvc.SimpleResult
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

trait Token[C <: OauthClient, SC <: OauthScope, CO <: OauthCode[RO, C], RO <: OauthResourceOwner, P <: OauthPermission[C], TO <: OauthToken[RO, C]] {

  this: ClientAuthentication[C] =>

  val logger:Logger = TokenEndpoint.logger

  def clientRepository: OauthClientRepository[C]
  def codeRepository: OauthCodeRepository[CO, RO, C]
  def tokenFactory: OauthTokenFactory[TO, RO, C]
  def tokenRepository: OauthTokenRepository[TO, RO, C]
  def supportedGrantType: Seq[String]

  type TokenValidation =  (TokenRequest, C) => ExecutionContext => Future[Option[OauthError]]
  type CodeValidation =  (TokenRequest, C, CO) => ExecutionContext => Future[Option[OauthError]]

  val clientGrantTypeValidation:TokenValidation = (tokenRequest, client) => implicit ec => {
    Future.successful {
      if(client.allowedGrantType.contains(tokenRequest.grantType)) None
      else Some(UnauthorizedClientError(Some(Messages(OAuth.ErrorUnauthorizedGrantType, tokenRequest.grantType))))
    }
  }

  val tokenValidator = Seq(clientGrantTypeValidation)

  val codeClientValidation:CodeValidation = (tokenRequest, client, code) => implicit ec => {
    Future.successful {
      if(code.client.id == client.id) None
      else Some(InvalidGrantError(Some(Messages(OAuth.ErrorClientNotMatch))))
    }
  }

  val codeExpireValidation:CodeValidation = (tokenRequest, client, code) => implicit ec => {
    Future.successful {
      if(new Date().getTime < (code.issueAt + code.expireIn)) None
      else Some(InvalidGrantError(Some(Messages(OAuth.ErrorExpiredAuthorizationCode))))
    }
  }

  val codeRevokeValidation:CodeValidation = (tokenRequest, client, code) => implicit ec => {
    Future.successful {
      if(code.revoked) Some(InvalidGrantError(Some(Messages(OAuth.ErrorRevokedAuthorizationCode))))
      else None
    }
  }

  val codeRedirectUriValidation:CodeValidation = (tokenRequest, client, code) => implicit ec => {
    Future.successful {
      tokenRequest match {
        case AuthorizationCodeTokenRequest(_, _, uri) =>
          code.redirectUri == uri match {
            case false if uri.isEmpty => Some(InvalidRequestError(Some(OAuth.ErrorRedirectURIMissing)))
            case false => Some(InvalidGrantError(Some(Messages(OAuth.ErrorRedirectURINotMatch))))
            case _ => None
          }
        case _ => None
      }
    }
  }

  val codeValidator = Seq(codeClientValidation, codeExpireValidation, codeRevokeValidation, codeRedirectUriValidation)

  def errorToJson(error: OauthError)(implicit writes: Writes[OauthError]) = Json.toJson(error)(writes)

  def clientOf(tokenRequest: TokenRequest)(implicit request: Request[AnyContentAsFormUrlEncoded], ec:ExecutionContext): Future[Either[Option[C], OauthError]] = tokenRequest match {
    case AuthorizationCodeTokenRequest(_, clientId, _) => clientRepository.find(clientId).map(Left(_))
    case _ => authenticate(request)
  }

  def onTokenFormError(f:Form[_ <: TokenRequest])(implicit request:Request[AnyContentAsFormUrlEncoded], ec:ExecutionContext, writes: Writes[OauthError]) = {
    Future.successful(BadRequest(errorToJson(InvalidRequestError(Some(f.errorsAsJson.toString())))))
  }

  def onTokenRequest(tokenRequest: TokenRequest)(f:(TokenRequest, C) => Request[AnyContentAsFormUrlEncoded] => Future[SimpleResult])(implicit request:Request[AnyContentAsFormUrlEncoded], ec:ExecutionContext, writes: Writes[OauthError]) = {

    clientOf(tokenRequest).flatMap(_.fold(_.fold(Future.successful(Unauthorized(errorToJson(InvalidClientError(Some(Messages(OAuth.ErrorClientNotFound))))))){ client =>
      Future.find(tokenValidator.map(_(tokenRequest, client)(ec)))(_.isDefined).flatMap {
        case Some(e) => Future.successful(BadRequest(errorToJson(e.get)))
        case _ => f(tokenRequest, client)(request)
      }
    }, error => Future.successful(Unauthorized(errorToJson(error)))))

  }

  def token(owner: (String, String) => Future[Option[RO]], clientOwner: C => Future[Option[RO]])
           (implicit ec:ExecutionContext, writes: Writes[TokenResponse], errorWrites: Writes[OauthError]): Request[AnyContentAsFormUrlEncoded] => Future[SimpleResult] =

    token(perform(owner, clientOwner))

  def token(f:(TokenRequest, C) => Request[AnyContentAsFormUrlEncoded] => Future[SimpleResult])(implicit ec:ExecutionContext, writes: Writes[TokenResponse], errorWrites: Writes[OauthError]): Request[AnyContentAsFormUrlEncoded] => Future[SimpleResult] = implicit request => {

    val query = request.body.data

    val formOrError: Either[Form[_ <: TokenRequest], OauthError] = query.get(OAuth.OauthGrantType).flatMap(_.headOption).fold[Either[Form[_ <: TokenRequest], OauthError]](Right(InvalidRequestError(Some(Messages(OAuth.ErrorUnsupportedGrantType))))){ grantType =>
      Option(grantType).filter(supportedGrantType.contains)
        .flatMap(tokenForms.get)
        .map(_.bindFromRequest)
        .toLeft(UnsupportedGrantTypeError(Some(Messages(OAuth.ErrorUnsupportedGrantType, grantType))))
    }

    formOrError.fold(form => {
      Option(query.filter(_._2.length > 1)).filterNot(_.isEmpty).map { params =>
        form.withGlobalError(Messages(OAuth.ErrorMultipleParameters, params.keySet.mkString(",")))
      }.getOrElse(form).fold(onTokenFormError, onTokenRequest(_)(f))
    }, error => {
      Future.successful(BadRequest(errorToJson(error)))
    })

  }

  def perform(owner: (String, String) => Future[Option[RO]],
              clientOwner: C => Future[Option[RO]]) (implicit ec:ExecutionContext, writes: Writes[TokenResponse], errorWrites: Writes[OauthError]): (TokenRequest, C) => Request[AnyContentAsFormUrlEncoded] => Future[SimpleResult] =
    
    (tokenRequest, oauthClient) => request => {
      tokenRequest match {
        case t:AuthorizationCodeTokenRequest =>
          codeRepository.find(t.code).flatMap(_.fold(Future.successful(BadRequest(errorToJson(InvalidGrantError(Some(Messages(OAuth.ErrorUnknownAuthorizationCode, t.code))))))){ code =>
            Future.find(codeValidator.map(_(tokenRequest, oauthClient, code)(ec)))(_.isDefined).flatMap {
              case Some(e) => Future.successful(BadRequest(errorToJson(e.get)))
              case _ => for {
                consumed <- codeRepository.revoke(t.code)
                token <- issueAToken(code.owner, code.client, t.redirectUri, code.scopes)
              } yield token
            }
          })

        case t:PasswordTokenRequest =>
          owner(t.username, t.password).flatMap(_.fold(Future.successful(BadRequest(errorToJson(InvalidGrantError(Some(Messages(OAuth.ErrorInvalidCredentials))))))){ resourceOwner =>
             issueAToken(resourceOwner, oauthClient, None, t.scope)
          })

        case t:ClientCredentialsTokenRequest =>
          clientOwner(oauthClient).flatMap(_.fold(Future.successful(BadRequest(errorToJson(InvalidGrantError(Some(Messages(OAuth.ErrorInvalidCredentials))))))){ resourceOwner =>
            issueAToken(resourceOwner, oauthClient, None, t.scope)
          })

        case t:RefreshTokenRequest =>
          tokenRepository.findForRefreshToken(t.refreshToken).flatMap(_.fold(Future.successful(BadRequest(errorToJson(InvalidGrantError(Some(Messages(OAuth.ErrorClientNotFound))))))){ previousToken =>
            for {
              Some(revoked) <- tokenRepository.revoke(previousToken.accessToken)
              token <- issueAToken(revoked.owner, revoked.client, None, revoked.scope)
            } yield token
          })

        //Can not happen
        case _ => Future.successful(BadRequest(errorToJson(UnsupportedGrantTypeError(Some(Messages(OAuth.ErrorUnsupportedGrantType))))))
      }
    }

  def issueAToken(owner: RO, client: C, redirectUri: Option[String], scope: Option[Seq[String]])(implicit ec:ExecutionContext, writes: Writes[TokenResponse]): Future[SimpleResult] =
    tokenFactory(owner, client, redirectUri, scope).map { token =>
      Ok(Json.toJson(TokenResponse(token)))
        .withHeaders("Cache-Control" -> "no-store", "Pragma" -> "no-cache")
    }

  def info(token: String)
          (authenticate: RequestHeader => Future[Option[C]])
          (ok: TO => Future[SimpleResult])
          (onUnauthorized: Future[SimpleResult] = Future.successful(Unauthorized("")),
           onTokenNotFound: Future[SimpleResult] = Future.successful(NotFound("")),
           onForbidden: Future[SimpleResult] = Future.successful(Forbidden("")))
          (implicit ec: ExecutionContext): RequestHeader => Future[SimpleResult] = request => {

    authenticate(request).flatMap {
      _.fold(onUnauthorized) { client =>
        tokenRepository.find(token).flatMap {
          _.fold(onTokenNotFound) { token =>
            if(token.client.id == client.id)
              ok(token)
            else
              onForbidden
          }
        }
      }
    }

  }
}

class TokenEndpoint[C <: OauthClient, SC <: OauthScope, CO <: OauthCode[RO, C], RO <: OauthResourceOwner, P <: OauthPermission[C], TO <: OauthToken[RO, C]](
  val clientRepository: OauthClientRepository[C],
  val codeRepository: OauthCodeRepository[CO, RO, C],
  val tokenFactory: OauthTokenFactory[TO, RO, C],
  val tokenRepository: OauthTokenRepository[TO, RO, C],
  val supportedGrantType: Seq[String] = OAuth.GrantType.All
) extends Token[C, SC, CO, RO, P, TO] {
  this: ClientAuthentication[C] =>
}

object TokenEndpoint {
  val logger:Logger = Logger(getClass)
}