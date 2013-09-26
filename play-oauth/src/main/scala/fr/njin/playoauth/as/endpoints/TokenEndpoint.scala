package fr.njin.playoauth.as.endpoints

import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future}
import play.api.data.Form
import fr.njin.playoauth.common.OAuth
import play.api.i18n.Messages
import fr.njin.playoauth.common.domain._
import fr.njin.playoauth.common
import scala.Predef._
import scala.Some
import play.api.mvc.SimpleResult
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

/**
 * User: bathily
 * Date: 17/09/13
 */

trait ClientAuthentication[T <: OauthClient] {
  def authenticate(request: Request[AnyContent])(implicit ec: ExecutionContext): Future[Either[Option[T], OauthError]]
}

class TokenEndpoint[I <: OauthClientInfo,T <: OauthClient, SC <: OauthScope, CO <: OauthCode[RO, P, T], RO <: OauthResourceOwner[T, P], P <: OauthPermission[T], TO <: OauthToken[RO, P, T]](
  clientFactory: OauthClientFactory[I , T],
  clientRepository: OauthClientRepository[T],
  scopeRepository: OauthScopeRepository[SC],
  codeFactory: OauthCodeFactory[CO, RO, P, T],
  codeRepository: OauthCodeRepository[CO, RO, P, T],
  tokenFactory: OauthTokenFactory[TO, RO, P, T],
  tokenRepository: OauthTokenRepository[TO, RO, P, T],
  supportedGrantType: Seq[String] = OAuth.GrantType.All
) extends common.Logger {

  this: ClientAuthentication[T] =>

  type TokenValidation =  (TokenRequest, T) => ExecutionContext => Future[Option[OauthError]]
  type CodeValidation =  (TokenRequest, T, CO) => ExecutionContext => Future[Option[OauthError]]

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
      if(code.revokedAt.isDefined) Some(InvalidGrantError(Some(Messages(OAuth.ErrorRevokedAuthorizationCode))))
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

  def clientOf(tokenRequest: TokenRequest)(implicit request: Request[AnyContent], ec:ExecutionContext): Future[Either[Option[T], OauthError]] = tokenRequest match {
    case AuthorizationCodeTokenRequest(_, clientId, _) => clientRepository.find(clientId).map(Left(_))
    case _ => authenticate(request)
  }

  def onTokenFormError(f:Form[_ <: TokenRequest])(implicit request:Request[AnyContent], ec:ExecutionContext, writes: Writes[OauthError]) = {
    Future.successful(BadRequest(errorToJson(InvalidRequestError(Some(f.errorsAsJson.toString())))))
  }

  def onTokenRequest(tokenRequest: TokenRequest)(f:(TokenRequest, T) => Request[AnyContent] => Future[SimpleResult])(implicit request:Request[AnyContent], ec:ExecutionContext, writes: Writes[OauthError]) = {

    clientOf(tokenRequest).flatMap(_.fold(_.fold(Future.successful(Unauthorized(errorToJson(InvalidClientError(Some(Messages(OAuth.ErrorClientNotFound))))))){ client =>
      Future.find(tokenValidator.map(_(tokenRequest, client)(ec)))(_.isDefined).flatMap(_ match {
        case Some(e) => Future.successful(BadRequest(errorToJson(e.get)))
        case _ => f(tokenRequest, client)(request)
      })
    }, error => Future.successful(Unauthorized(errorToJson(error)))))

  }

  def token(f:(TokenRequest, T) => Request[AnyContent] => Future[SimpleResult])(implicit ec:ExecutionContext, writes: Writes[TO], errorWrites: Writes[OauthError]) =
    Action.async { implicit request =>

      val formOrError: Either[Form[_ <: TokenRequest], OauthError] = request.getQueryString(OAuth.OauthGrantType).fold[Either[Form[_ <: TokenRequest], OauthError]](Right(InvalidRequestError(Some(Messages(OAuth.ErrorUnsupportedGrantType))))){ grantType =>
        Option(grantType).filter(supportedGrantType.contains)
          .flatMap(tokenForms.get)
          .map(_.bindFromRequest)
          .toLeft(UnsupportedGrantTypeError(Some(Messages(OAuth.ErrorUnsupportedGrantType, grantType))))
      }

      formOrError.fold(form => {
        Option(request.queryString.filter(_._2.length > 1)).filterNot(_.isEmpty).map { params =>
          form.withGlobalError(Messages(OAuth.ErrorMultipleParameters, params.keySet.mkString(",")))
        }.getOrElse(form).fold(onTokenFormError, onTokenRequest(_)(f))
      }, error => {
        Future.successful(BadRequest(errorToJson(error)))
      })

    }

  def perform(owner: (String, String) => ExecutionContext => Future[Option[RO]], clientOwner: T  => ExecutionContext => Future[Option[RO]])(implicit ec:ExecutionContext, writes: Writes[TO], errorWrites: Writes[OauthError]): (TokenRequest, T) => Request[AnyContent] => Future[SimpleResult] = (tokenRequest, oauthClient) => implicit request => {
    tokenRequest match {
      case t:AuthorizationCodeTokenRequest =>
        codeRepository.find(t.code).flatMap(_.fold(Future.successful(BadRequest(errorToJson(InvalidGrantError(Some(Messages(OAuth.ErrorUnknownAuthorizationCode, t.code))))))){ code =>
          Future.find(codeValidator.map(_(tokenRequest, oauthClient, code)(ec)))(_.isDefined).flatMap(_ match {
            case Some(e) => Future.successful(BadRequest(errorToJson(e.get)))
            case _ => issueAToken(code.owner, code.client, t.redirectUri, code.scopes)
          })
        })

      case t:PasswordTokenRequest =>
        owner(t.username, t.password)(ec).flatMap(_.fold(Future.successful(BadRequest(errorToJson(InvalidGrantError(Some(Messages(OAuth.ErrorInvalidCredentials))))))){ resourceOwner =>
           issueAToken(resourceOwner, oauthClient, None, t.scope)
        })

      case t:ClientCredentialsTokenRequest =>
        clientOwner(oauthClient)(ec).flatMap(_.fold(Future.successful(BadRequest(errorToJson(InvalidGrantError(Some(Messages(OAuth.ErrorInvalidCredentials))))))){ resourceOwner =>
          issueAToken(resourceOwner, oauthClient, None, t.scope)
        })

      case t:RefreshTokenRequest =>
        tokenRepository.findForRefreshToken(t.refreshToken)(ec).flatMap(_.fold(Future.successful(BadRequest(errorToJson(InvalidGrantError(Some(Messages(OAuth.ErrorClientNotFound))))))){ previousToken =>
          for {
            Some(revoked) <- tokenRepository.revoke(previousToken.accessToken)(ec)
            token <- issueAToken(revoked.owner, revoked.client, None, revoked.scope)
          } yield token
        })

      //Can not happen
      case _ => Future.successful(BadRequest(errorToJson(UnsupportedGrantTypeError(Some(Messages(OAuth.ErrorUnsupportedGrantType))))))
    }
  }

  def issueAToken(owner: RO, client: T, redirectUri: Option[String], scope: Option[Seq[String]])(implicit ec:ExecutionContext, writes: Writes[TO]): Future[SimpleResult] =
    tokenFactory(owner, client, redirectUri, scope).flatMap {
      tokenRepository.save(_).map { token =>
        Ok(Json.toJson(token))
          .withHeaders("Cache-Control" -> "no-store", "Pragma" -> "no-cache")
      }
    }
}