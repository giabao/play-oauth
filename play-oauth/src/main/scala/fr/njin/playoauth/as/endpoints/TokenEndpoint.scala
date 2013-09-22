package fr.njin.playoauth.as.endpoints

import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future}
import play.api.data.Form
import fr.njin.playoauth.common.OAuth
import play.api.i18n.Messages
import fr.njin.playoauth.common.domain._
import fr.njin.playoauth.common
import scala.util.Either
import scala.Predef._
import scala.Some
import play.api.mvc.SimpleResult
import Results._
import play.api.http.Status._
import OauthError._
import play.api.libs.json.Json
import fr.njin.playoauth.common.request.{TokenRequest, AuthzRequest}
import Requests._

/**
 * User: bathily
 * Date: 17/09/13
 */
class TokenEndpoint[I <: OauthClientInfo,T <: OauthClient, SC <: OauthScope, CO <: OauthCode[RO, P, T], RO <: OauthResourceOwner[T, P], P <: OauthPermission[T]](
  clientFactory: OauthClientFactory[I , T],
  clientRepository: OauthClientRepository[T],
  scopeRepository: OauthScopeRepository[SC],
  codeFactory: OauthCodeFactory[CO, RO, P, T],
  codeRepository: OauthCodeRepository[CO, RO, P, T]
) extends common.Logger {

  type TokenValidation =  (TokenRequest, T) => ExecutionContext => Future[Either[Boolean, OauthError]]

  val grantTypeCodeValidation:TokenValidation = (tokenRequest, client) => implicit ec => { Future.successful {
    Some(OAuth.GrantType.All.contains(tokenRequest.grantType)).filter(_ == true).toLeft(UnsupportedGrantTypeError())
  }}

  val clientGrantTypeValidation:TokenValidation = (tokenRequest, client) => implicit ec => { Future.successful {
    Some(client.allowedGrantType.contains(tokenRequest.grantType)).filter(_ == true).toLeft(UnauthorizedClientError(Some(Messages(OAuth.ErrorUnauthorizedGrantType, tokenRequest.grantType))))
  }}

  val tokenValidator = Seq(grantTypeCodeValidation, clientGrantTypeValidation)

  def onTokenFormError(f:Form[TokenRequest])(implicit request:Request[AnyContent], ec:ExecutionContext) = {
    Future.successful(BadRequest(Json.toJson(InvalidRequestError(Some(f.errorsAsJson.toString())))))
  }

  def onTokenRequest(tokenRequest: TokenRequest)(f:(TokenRequest, T) => Request[AnyContent] => Future[SimpleResult])(implicit request:Request[AnyContent], ec:ExecutionContext) = {
    clientRepository.find(tokenRequest.clientId).flatMap{ _.fold(Future.successful(Unauthorized(Json.toJson(InvalidClientError(Some(Messages(OAuth.ErrorClientNotFound, tokenRequest.clientId))))))){ client =>
      Future.find(tokenValidator.map(_(tokenRequest, client)(ec)))(_.isRight).flatMap(_ match {
        case Some(e) => Future.successful(BadRequest(Json.toJson(e.right.get)))
        case _ => f(tokenRequest, client)(request)
      })
    }}
  }

  def token(f:(TokenRequest, T) => Request[AnyContent] => Future[SimpleResult])(implicit ec:ExecutionContext) =
    Action.async { implicit request =>
      tokenRequestForm.bindFromRequest.fold(onTokenFormError, onTokenRequest(_)(f))
    }

  def perform(implicit ec:ExecutionContext): (TokenRequest, T) => Request[AnyContent] => Future[SimpleResult] = (tokenRequest, oauthClient) => implicit request => {
    codeRepository.find(tokenRequest.code).flatMap(_.fold(Future.successful(BadRequest(Json.toJson(InvalidGrantError(Some(Messages(OAuth.ErrorUnknownAuthorizationCode, tokenRequest.code))))))){ code =>
      Future.successful(Ok(""))
    })
  }
}