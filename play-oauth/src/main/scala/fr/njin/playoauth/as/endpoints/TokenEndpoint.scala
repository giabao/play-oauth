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
import OauthError._
import play.api.libs.json.{Writes, Json}
import fr.njin.playoauth.common.request.TokenRequest
import Requests._
import java.util.Date

/**
 * User: bathily
 * Date: 17/09/13
 */
class TokenEndpoint[I <: OauthClientInfo,T <: OauthClient, SC <: OauthScope, CO <: OauthCode[RO, P, T], RO <: OauthResourceOwner[T, P], P <: OauthPermission[T], TO <: OauthToken](
  clientFactory: OauthClientFactory[I , T],
  clientRepository: OauthClientRepository[T],
  scopeRepository: OauthScopeRepository[SC],
  codeFactory: OauthCodeFactory[CO, RO, P, T],
  codeRepository: OauthCodeRepository[CO, RO, P, T],
  tokenFactory: OauthTokenFactory[TO, CO, RO, P, T],
  tokenRepository: OauthTokenRepository[TO],
  supportedGrantType: Seq[String] = OAuth.GrantType.All
) extends common.Logger {

  type TokenValidation =  (TokenRequest, T) => ExecutionContext => Future[Option[OauthError]]
  type CodeValidation =  (TokenRequest, T, CO) => ExecutionContext => Future[Option[OauthError]]

  val grantTypeCodeValidation:TokenValidation = (tokenRequest, client) => implicit ec => {
    Future.successful {
      if(supportedGrantType.contains(tokenRequest.grantType)) None
      else Some(UnsupportedGrantTypeError(Some(Messages(OAuth.ErrorUnsupportedGrantType, tokenRequest.grantType))))
    }
  }

  val clientGrantTypeValidation:TokenValidation = (tokenRequest, client) => implicit ec => {
    Future.successful {
      if(client.allowedGrantType.contains(tokenRequest.grantType)) None
      else Some(UnauthorizedClientError(Some(Messages(OAuth.ErrorUnauthorizedGrantType, tokenRequest.grantType))))
    }
  }

  val tokenValidator = Seq(grantTypeCodeValidation, clientGrantTypeValidation)

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
      code.redirectUri == tokenRequest.redirectUri match {
        case false if tokenRequest.redirectUri.isEmpty => Some(InvalidRequestError(Some(OAuth.ErrorRedirectURIMissing)))
        case false => Some(InvalidGrantError(Some(Messages(OAuth.ErrorRedirectURINotMatch))))
        case _ => None
      }
    }
  }

  val codeValidator = Seq(codeClientValidation, codeExpireValidation, codeRevokeValidation, codeRedirectUriValidation)

  def onTokenFormError(f:Form[TokenRequest])(implicit request:Request[AnyContent], ec:ExecutionContext) = {
    Future.successful(BadRequest(Json.toJson(InvalidRequestError(Some(f.errorsAsJson.toString())))))
  }

  def onTokenRequest(tokenRequest: TokenRequest)(f:(TokenRequest, T) => Request[AnyContent] => Future[SimpleResult])(implicit request:Request[AnyContent], ec:ExecutionContext) = {
    clientRepository.find(tokenRequest.clientId).flatMap{ _.fold(Future.successful(Unauthorized(Json.toJson(InvalidClientError(Some(Messages(OAuth.ErrorClientNotFound, tokenRequest.clientId))))))){ client =>
      Future.find(tokenValidator.map(_(tokenRequest, client)(ec)))(_.isDefined).flatMap(_ match {
        case Some(e) => Future.successful(BadRequest(Json.toJson(e.get)))
        case _ => f(tokenRequest, client)(request)
      })
    }}
  }

  def token(f:(TokenRequest, T) => Request[AnyContent] => Future[SimpleResult])(implicit ec:ExecutionContext) =
    Action.async { implicit request =>
      val form = tokenRequestForm.bindFromRequest

      Option(request.queryString.filter(_._2.length > 1)).filterNot(_.isEmpty).map { params =>
        form.withGlobalError(Messages(OAuth.ErrorMultipleParameters, params.keySet.mkString(",")))
      }.getOrElse(form).fold(onTokenFormError, onTokenRequest(_)(f))

    }

  def perform(implicit ec:ExecutionContext, writes: Writes[TO]): (TokenRequest, T) => Request[AnyContent] => Future[SimpleResult] = (tokenRequest, oauthClient) => implicit request => {
    codeRepository.find(tokenRequest.code).flatMap(_.fold(Future.successful(BadRequest(Json.toJson(InvalidGrantError(Some(Messages(OAuth.ErrorUnknownAuthorizationCode, tokenRequest.code))))))){ code =>
      Future.find(codeValidator.map(_(tokenRequest, oauthClient, code)(ec)))(_.isDefined).flatMap(_ match {
        case Some(e) => Future.successful(BadRequest(Json.toJson(e.get)))
        case _ => {
          tokenFactory(code, tokenRequest.redirectUri).flatMap {
            tokenRepository.save(_).map { token =>
              Ok(Json.toJson(token))
            }
          }
        }
      })
    })
  }
}