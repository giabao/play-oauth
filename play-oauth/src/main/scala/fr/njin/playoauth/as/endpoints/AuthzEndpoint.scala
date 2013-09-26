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
import fr.njin.playoauth.common.request.AuthzRequest
import Requests._

/**
 * User: bathily
 * Date: 17/09/13
 */
class AuthzEndpoint[I <: OauthClientInfo,T <: OauthClient, SC <: OauthScope, CO <: OauthCode[RO, P, T], RO <: OauthResourceOwner[T, P], P <: OauthPermission[T], TO <: OauthToken](
  clientFactory: OauthClientFactory[I , T],
  clientRepository: OauthClientRepository[T],
  scopeRepository: OauthScopeRepository[SC],
  codeFactory: OauthCodeFactory[CO, RO, P, T],
  codeRepository: OauthCodeRepository[CO, RO, P, T],
  tokenFactory: OauthTokenFactory[TO, RO, P, T],
  tokenRepository: OauthTokenRepository[TO],
  supportedResponseType: Seq[String] = OAuth.ResponseType.All
) extends common.Logger {

  type AuthzValidation =  (AuthzRequest, T) => ExecutionContext => Future[Option[Map[String, Seq[String]]]]

  val responseTypeCodeValidation:AuthzValidation = (authzRequest, client) => implicit ec => { Future.successful {
    if(supportedResponseType.contains(authzRequest.responseType)) None else Some(UnsupportedResponseTypeError())
  }}

  val scopeValidation:AuthzValidation = (authzRequest, client) => implicit ec => {
    authzRequest.scope.map[Future[Option[Map[String, Seq[String]]]]]{ scope =>
        scopeRepository.find(scope : _*).map{ scopes =>
          val errors = scopes.filterNot(_._2.isDefined)
          if(errors.isEmpty) None else Some(InvalidScopeError(Some(Messages(OAuth.ErrorInvalidScope, errors.map(e => e._1).mkString(" ")))))
        }
    }.getOrElse(Future.successful(None))
  }

  val clientAuthorizedValidation:AuthzValidation = (authzRequest, client) => implicit ec => {Future.successful {
    if(client.authorized) None else Some(AccessDeniedError())
  }}

  val clientResponseTypeValidation:AuthzValidation = (authzRequest, client) => implicit ec => { Future.successful {
    if(client.allowedResponseType.contains(authzRequest.responseType)) None else Some(UnauthorizedClientError(Some(Messages(OAuth.ErrorUnauthorizedResponseType, authzRequest.responseType))))
  }}

  val authzValidator = Seq(responseTypeCodeValidation, scopeValidation, clientAuthorizedValidation, clientResponseTypeValidation)

  def errorToQuery(f:Form[_]):Map[String, Seq[String]] = queryWithState(InvalidRequestError(Some(f.errorsAsJson.toString())), f(OAuth.OauthState).value)

  def queryWithState(query: Map[String, Seq[String]], state:Option[String]):Map[String, Seq[String]] =
     state.map(s => query + (OAuth.OauthState -> Seq(s))).getOrElse(query)

  def register(allowedResponseType: Seq[String], allowedGrantType: Seq[String], info:I)(implicit ec:ExecutionContext): Future[T] = {
    clientFactory.apply(allowedResponseType, allowedGrantType, info).flatMap(clientRepository.save)
  }

  def deRegister(client:T)(implicit ec:ExecutionContext): Future[Unit] = {
    clientRepository.delete(client)
  }

  def onFormError(f:Form[AuthzRequest])(implicit request:Request[AnyContent], ec:ExecutionContext) = {
    f.error(OAuth.OauthClientId).map(e => Future.successful(BadRequest(Messages(OAuth.ErrorClientMissing))))
      .orElse(f.error(OAuth.OauthRedirectUri).map(e => Future.successful(BadRequest(Messages(OAuth.ErrorRedirectURIInvalid, e.args)))))
      .getOrElse {
      val id = f(OAuth.OauthClientId).value.get
      clientRepository.find(id).map(_.fold(NotFound(Messages(OAuth.ErrorClientNotFound, id))) { client =>

        def responseTo(uri: Option[String]) =  uri.fold(BadRequest(Messages(OAuth.ErrorRedirectURIMissing))) { url =>
          Redirect(url, errorToQuery(f), FOUND)
        }

        (f(OAuth.OauthResponseType).value, f(OAuth.OauthRedirectUri).value) match {
          case ((Some(OAuth.ResponseType.Token), Some(uri))) =>
            if (client.redirectUris.exists(_.contains(uri))) responseTo(Some(uri)) else BadRequest(Messages(OAuth.ErrorRedirectURINotMatch, uri))
          case ((Some(OAuth.ResponseType.Code)), uri) => responseTo(uri.orElse(client.redirectUri))
          case _ => responseTo(client.redirectUri)
        }

      })
    }
  }

  def onAuthzRequest(authzRequest: AuthzRequest)(f:(AuthzRequest, T) => Request[AnyContent] => Future[SimpleResult])(implicit request:Request[AnyContent], ec:ExecutionContext) = {
    clientRepository.find(authzRequest.clientId).flatMap{ _.fold(Future.successful(NotFound(Messages(OAuth.ErrorClientNotFound, authzRequest.clientId)))){ client =>
      authzRequest.redirectUri.orElse(client.redirectUri).fold(Future.successful(BadRequest(Messages(OAuth.ErrorRedirectURIMissing)))) { url =>
        Future.find(authzValidator.map(_(authzRequest, client)(ec)))(_.isDefined).flatMap(_ match {
          case Some(e) => Future.successful(Redirect(url, queryWithState(e.get, authzRequest.state), FOUND))
          case _ => f(authzRequest, client)(request)
        })
      }
    }}
  }

  def authorize(f:(AuthzRequest, T) => Request[AnyContent] => Future[SimpleResult])(implicit ec:ExecutionContext) =
    Action.async { implicit request =>

      val form = authorizeRequestForm.bindFromRequest

      Option(request.queryString.filter(_._2.length > 1)).filterNot(_.isEmpty).map { params =>
        form.withGlobalError(Messages(OAuth.ErrorMultipleParameters, params.keySet.mkString(",")))
      }.getOrElse(form).fold(onFormError, onAuthzRequest(_)(f))

    }

  def perform(owner:(Request[AnyContent]) => Option[RO])
               (onUnauthenticated:(AuthzRequest, T) => Request[AnyContent] => Future[SimpleResult],
                onUnauthorized:(AuthzRequest, T) => Request[AnyContent] => Future[SimpleResult])
               (implicit ec:ExecutionContext): (AuthzRequest, T) => Request[AnyContent] => Future[SimpleResult] = (authzRequest, oauthClient) => implicit request => {
    owner(request).fold(onUnauthenticated(authzRequest, oauthClient)(request)) { resourceOwner =>
      resourceOwner.permission(oauthClient).fold(onUnauthorized(authzRequest, oauthClient)(request)) { permission =>
        if(permission.authorized(authzRequest))
          codeFactory.apply(resourceOwner, oauthClient, authzRequest.redirectUri, authzRequest.scope)
            .flatMap(codeRepository.save)
            .flatMap { code =>
              authzRequest.responseType match {
                case OAuth.ResponseType.Code =>
                  authzAccept(code)(authzRequest, oauthClient)(request)
                case OAuth.ResponseType.Token =>
                  tokenFactory(code.owner, code.client, authzRequest.redirectUri, code.scopes).flatMap {
                    tokenRepository.save(_).flatMap { token =>
                      authzAccept(token)(authzRequest, oauthClient)(request)
                    }
                  }
              }
            }
        else
          authzDeny(authzRequest, oauthClient)(request)
      }
    }
  }

  def authzAccept(code:CO): (AuthzRequest, T) => Request[AnyContent] => Future[SimpleResult] = (authzRequest, oauthClient) => implicit request => {
    val url = authzRequest.redirectUri.orElse( oauthClient.redirectUri).get
    Future.successful(Redirect(url, queryWithState(Map(OAuth.OauthCode -> Seq(code.value)), authzRequest.state), FOUND))
  }

  def authzAccept(token:TO): (AuthzRequest, T) => Request[AnyContent] => Future[SimpleResult] = (authzRequest, oauthClient) => implicit request => {
    import fr.njin.playoauth.as.Utils.toUrlFragment

    val url = authzRequest.redirectUri.orElse( oauthClient.redirectUri).get
    Future.successful(Redirect(url + toUrlFragment(
      Map(
        OAuth.OauthAccessToken -> Seq(token.accessToken),
        OAuth.OauthTokenType -> Seq(token.tokenType)
      ) ++ token.expiresIn.map(s => OAuth.OauthExpiresIn -> Seq(s.toString))
        ++ token.scope.map(s => OAuth.OauthScope -> Seq(s.mkString(" ")))
        ++ authzRequest.state.map(s => OAuth.OauthState -> Seq(s))), FOUND)
    )
  }


  def authzDeny: (AuthzRequest, T) => Request[AnyContent] => Future[SimpleResult] = (authzRequest, oauthClient) => implicit request => {
    val url = oauthClient.redirectUri.orElse(authzRequest.redirectUri).get
    Future.successful(Redirect(url, queryWithState(AccessDeniedError(), authzRequest.state), FOUND))
  }

}