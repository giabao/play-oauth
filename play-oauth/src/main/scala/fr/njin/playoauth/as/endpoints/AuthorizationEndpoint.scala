package fr.njin.playoauth.as.endpoints

import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future}
import play.api.data.Form
import fr.njin.playoauth.common.OAuth
import play.api.i18n.Messages
import fr.njin.playoauth.common.domain._
import scala.Predef._
import Results._
import play.api.http.Status._
import fr.njin.playoauth.as.OauthError._
import fr.njin.playoauth.common.request.AuthzRequest
import Requests._
import play.api.Logger
import fr.njin.playoauth.Utils
import scala.Some
import play.api.mvc.SimpleResult

/**
 * Authorization endpoint
 *
 * To create your authorization endpoint, instantiate an [[fr.njin.playoauth.as.endpoints.AuthorizationEndpoint]]
 * and call the authorize method in your action.
 *
 * {{{
 *   def authz = Action.async { request =>
 *    new AuthorizationEndpoint(...).authorize(...)(..., ...).apply(request)
 *   }
 * }}}
 *
 * @tparam C Client type
 * @tparam SC Scope type
 * @tparam CO Code type
 * @tparam RO Resource owner type
 * @tparam P Permission type
 * @tparam TO Token type
 */
trait Authorization[C <: OauthClient, SC <: OauthScope, CO <: OauthCode[RO, C], RO <: OauthResourceOwner,
                    P <: OauthPermission[C], TO <: OauthToken[RO, C]] {

  val logger:Logger = AuthorizationEndpoint.logger

  def permissions: OauthResourceOwnerPermission[RO, C, P]
  def clientRepository: OauthClientRepository[C]
  def scopeRepository: OauthScopeRepository[SC]
  def codeFactory: OauthCodeFactory[CO, RO, C]
  def tokenFactory: OauthTokenFactory[TO, RO, C]
  def supportedResponseType: Seq[String]

  /**
   * Alias for Authorization request validation function
   *
   *
   * A validator takes an authorization request and a client then return the eventual errors.
   */
  type AuthzReqValidator =  (AuthzRequest, C) => ExecutionContext => Future[Option[Map[String, Seq[String]]]]

  /**
   * Validates response type of the request.
   *
   * If [[supportedResponseType]] does not contain the requested response type,
   * a [[fr.njin.playoauth.as.OauthError.unsupportedResponseTypeError]] is returned
   */
  val responseTypeCodeValidator:AuthzReqValidator = (authzRequest, client) => implicit ec => { Future.successful {
    if(supportedResponseType.contains(authzRequest.responseType)) {
      None
    } else {
      Some(unsupportedResponseTypeError())
    }
  }}

  /**
   * Validates the scope of the request.
   *
   * All values of the requested scopes must be found by the scopeRepository
   * otherwise a [[fr.njin.playoauth.as.OauthError.invalidScopeError]] is returned
   * with the missing scopes
   */
  val scopeValidator:AuthzReqValidator = (authzRequest, client) => implicit ec => {
    authzRequest.scopes.map[Future[Option[Map[String, Seq[String]]]]]{ scope =>
        scopeRepository.find(scope : _*).map{ scopes =>
          val errors = scope.filterNot(scopes.get(_).isDefined)
          if(errors.isEmpty) {
            None
          } else {
            Some(invalidScopeError(Some(Messages(OAuth.ErrorInvalidScope, errors.mkString(" ")))))
          }
        }
    }.getOrElse(Future.successful(None))
  }

  /**
   * Validates the access of the client
   *
   * If the client is not authorized a [[fr.njin.playoauth.as.OauthError.accessDeniedError]] is returned
   */
  val clientAuthorizedValidator:AuthzReqValidator = (authzRequest, client) => implicit ec => {Future.successful {
    if(client.authorized) {
      None
    } else {
      Some(accessDeniedError())
    }
  }}

  /**
   * Validates the client's response type
   *
   * If [[fr.njin.playoauth.common.domain.OauthClient.allowedResponseType]] does not contain the requested response type,
   * a [[fr.njin.playoauth.as.OauthError.unauthorizedClientError]] is returned
   */
  val clientResponseTypeValidator:AuthzReqValidator = (authzRequest, client) => implicit ec => { Future.successful {
    if(client.allowedResponseType.contains(authzRequest.responseType)) {
      None
    } else {
      Some(unauthorizedClientError(Some(Messages(OAuth.ErrorUnauthorizedResponseType, authzRequest.responseType))))
    }
  }}

  /**
   * All validators that will be tested for the request
   */
  lazy val authzValidator = List(
    responseTypeCodeValidator,
    scopeValidator,
    clientAuthorizedValidator,
    clientResponseTypeValidator
  )

  /**
   * Helper which builds the http query from all form's errors
   *
   * @param f the form
   * @return the query
   */
  def errorToQuery(f:Form[_]):Map[String, Seq[String]] =
    queryWithState(invalidRequestError(Some(f.errorsAsJson.toString())), f(OAuth.OauthState).value)

  def queryWithState(query: Map[String, Seq[String]], state:Option[String]):Map[String, Seq[String]] =
     state.map(s => query + (OAuth.OauthState -> Seq(s))).getOrElse(query)

  /**
   * Error handler
   *
   *
   * According to the specification,
   *
   * <ul>
   * <li>if the client id or the redirect url is invalid, a bad request is returned</li>
   * <li>if the client is unknown, a not found is returned</li>
   * <li>otherwise, a redirection to the requested redirect url or the client redirect url is returned</li>
   * </ul>
   *
   * @param f the request form
   * @param toQuery a function which builds the result query
   * @param request the http request header
   * @param ec an execution context
   * @return the result of the authorization
   */
  def onError(f:Form[AuthzRequest], toQuery: Form[AuthzRequest] => Map[String, Seq[String]])
             (implicit request:RequestHeader, ec:ExecutionContext): Future[SimpleResult] = {

    f.error(OAuth.OauthClientId).map(e => Future.successful(BadRequest(Messages(OAuth.ErrorClientMissing))))
      .orElse(f.error(OAuth.OauthRedirectUri).map(e =>
        Future.successful(BadRequest(Messages(OAuth.ErrorRedirectURIInvalid, e.args)))
      )).getOrElse {
        val id = f(OAuth.OauthClientId).value.get
        clientRepository.find(id).map(_.fold(NotFound(Messages(OAuth.ErrorClientNotFound, id))) { client =>

          def responseTo(uri: Option[String]): SimpleResult =  uri.fold(BadRequest(Messages(OAuth.ErrorRedirectURIMissing))) { url =>
            Redirect(url, toQuery(f), FOUND)
          }

          (f(OAuth.OauthResponseType).value, f(OAuth.OauthRedirectUri).value) match {
            case ((Some(OAuth.ResponseType.Token), Some(uri))) =>
              if (client.redirectUris.exists(_.contains(uri))) {
                responseTo(Some(uri))
              } else {
                BadRequest(Messages(OAuth.ErrorRedirectURINotMatch, uri))
              }
            case ((Some(OAuth.ResponseType.Code)), uri) => responseTo(uri.orElse(client.redirectUri))
            case _ => responseTo(client.redirectUri)
          }

      })
    }
  }

  def onFormError(f:Form[AuthzRequest])
                 (implicit request:RequestHeader, ec:ExecutionContext): Future[SimpleResult] =
    onError(f, errorToQuery)

  def onServerError(f:Form[AuthzRequest], error: Throwable)
                   (implicit request:RequestHeader, ec:ExecutionContext): Future[SimpleResult] =
    onError(f, f => queryWithState(serverError() ,f(OAuth.OauthState).value))

  /**
   * Request handler
   *
   * This method is called when the request is successfully bound.
   * It search the client and really addresses the request with the callback
   *
   * @param authzRequest the request
   * @param f the callback. Called when all validations pass
   * @param request the http request header
   * @param ec an execution context
   * @return the result of the authorization
   */
  def onAuthzRequest(authzRequest: AuthzRequest)
                    (f:(AuthzRequest, C) => RequestHeader => Future[SimpleResult])
                    (implicit request:RequestHeader, ec:ExecutionContext): Future[SimpleResult] = {

    clientRepository.find(authzRequest.clientId).flatMap{
      _.fold(Future.successful(NotFound(Messages(OAuth.ErrorClientNotFound, authzRequest.clientId)))){ client =>
        authzRequest.redirectUri.orElse(client.redirectUri).fold(
          Future.successful(BadRequest(Messages(OAuth.ErrorRedirectURIMissing)))) { url =>
          Future.find(authzValidator.map(_(authzRequest, client)(ec)))(_.isDefined).flatMap {
            case Some(e) => Future.successful(Redirect(url, queryWithState(e.get, authzRequest.state), FOUND))
            case _ => f(authzRequest, client)(request)
          }
        }
      }
    }

  }

  //FIXME Make a link to the other authorize method
  /**
   * Call the authorize with [[perform]] as the callback.
   *
   *
   * See [[perform]] for the arguments
   *
   * @param owner
   * @param onUnauthenticated
   * @param onUnauthorized
   * @param ec
   *
   * @return the result of the authorization
   */
  def authorize(owner:(RequestHeader) => Option[RO])
               (onUnauthenticated:(AuthzRequest, C) => RequestHeader => Future[SimpleResult],
                onUnauthorized:(AuthzRequest, C) => RequestHeader => Future[SimpleResult])
               (implicit ec:ExecutionContext): RequestHeader => Future[SimpleResult] =

    authorize(perform(owner)(onUnauthenticated, onUnauthorized))

  /**
   * THE endpoint
   *
   * Bind the form from the request and call [[onFormError]] if the form has an error,
   * [[onAuthzRequest]] otherwise.
   *
   * Use this method as your endpoint if you need more flexibility. See [[perform]]
   * as possible callback.
   *
   * @param f the callback to pass to [[onAuthzRequest]]
   * @param ec an execution context
   * @return the result of the authorization
   */
  def authorize(f:(AuthzRequest, C) => RequestHeader => Future[SimpleResult])
               (implicit ec:ExecutionContext): RequestHeader => Future[SimpleResult] =

    implicit request => {
      val query = request.queryString
      val form = authorizeRequestForm.bindFromRequest(query)

      Option(query.filter(_._2.length > 1)).filterNot(_.isEmpty).map { params =>
        form.withGlobalError(Messages(OAuth.ErrorMultipleParameters, params.keySet.mkString(",")))
      }.getOrElse(form).fold(onFormError, onAuthzRequest(_)(f)).recoverWith {
        case t:Throwable => onServerError(form, t)
      }
    }

  /**
   * A callback which can be use for the authorize method.
   *
   * @param owner A function that extract the current resource owner from the request.
   *              Return None if there is no resource owner.
   * @param onUnauthenticated A function called when no resource owner is found
   *                          (when owner return None). You can use it to show
   *                          a login page to the end user.
   * @param onUnauthorized    A function called when the resource owner didn't allow
   *                          the client to obtain a token (when permissions return None).
   *                          You can use it to show a decision form to the end user
   *                          then create a permission for the request.
   * @param ec an execution context
   * @return the result of the authorization
   */
  def perform(owner:(RequestHeader) => Option[RO])
               (onUnauthenticated:(AuthzRequest, C) => RequestHeader => Future[SimpleResult],
                onUnauthorized:(AuthzRequest, C) => RequestHeader => Future[SimpleResult])
               (implicit ec:ExecutionContext): (AuthzRequest, C) => RequestHeader => Future[SimpleResult] =

    (authzRequest, oauthClient) => implicit request => {
      owner(request).fold(onUnauthenticated(authzRequest, oauthClient)(request)) { resourceOwner =>
        permissions(resourceOwner, oauthClient).flatMap(_.fold(onUnauthorized(authzRequest, oauthClient)(request)) { permission =>
          if(permission.authorized(authzRequest)) {
            codeFactory(resourceOwner, oauthClient, authzRequest.redirectUri, authzRequest.scopes)
              .flatMap { code =>
                authzRequest.responseType match {
                  case OAuth.ResponseType.Code =>
                    authzAccept(code)(authzRequest, oauthClient)(request)
                  case OAuth.ResponseType.Token =>
                    tokenFactory(code.owner, code.client, authzRequest.redirectUri, code.scopes).flatMap { token =>
                      authzAccept(token)(authzRequest, oauthClient)(request)
                    }
                }
              }
          } else {
            authzDeny(authzRequest, oauthClient)(request)
          }
        })
      }
    }

  /**
   * Builds the success result for the created code
   *
   * <b>Caution : make sure that all validations passed before calling this method</b>
   *
   * @param code the newly created code
   * @return the authorization result
   */
  def authzAccept(code:CO): (AuthzRequest, C) => RequestHeader => Future[SimpleResult] =

    (authzRequest, oauthClient) => implicit request => {
      val url = authzRequest.redirectUri.orElse(oauthClient.redirectUri).get
      Future.successful(Redirect(url, queryWithState(Map(OAuth.OauthCode -> Seq(code.value)), authzRequest.state), FOUND))
    }

  /**
   * Builds the success result for the created token
   *
   * <b>Caution : make sure that all validations passed before calling this method</b>
   *
   * @param token the newly created token
   * @return the authorization result
   */
  def authzAccept(token:TO): (AuthzRequest, C) => RequestHeader => Future[SimpleResult] =

    (authzRequest, oauthClient) => implicit request => {

      import Utils.toUrlFragment

      val url = authzRequest.redirectUri.orElse( oauthClient.redirectUri).get
      Future.successful(Redirect(url + toUrlFragment(
        Map(
          OAuth.OauthAccessToken -> Seq(token.accessToken),
          OAuth.OauthTokenType -> Seq(token.tokenType)
        ) ++ token.expiresIn.map(s => OAuth.OauthExpiresIn -> Seq(s.toString))
          ++ token.scopes.map(s => OAuth.OauthScope -> Seq(s.mkString(" ")))
          ++ authzRequest.state.map(s => OAuth.OauthState -> Seq(s))), FOUND)
      )
    }


  /**
   * Builds the success result for the denied response
   *
   * <b>Caution : make sure that all validations passed before calling this method</b>
   *
   * @return the authorization result
   */
  def authzDeny: (AuthzRequest, C) => RequestHeader => Future[SimpleResult] =

    (authzRequest, oauthClient) => implicit request => {
      val url = oauthClient.redirectUri.orElse(authzRequest.redirectUri).get
      Future.successful(Redirect(url, queryWithState(accessDeniedError(), authzRequest.state), FOUND))
    }

}

class AuthorizationEndpoint[C <: OauthClient, SC <: OauthScope, CO <: OauthCode[RO, C], RO <: OauthResourceOwner,
                            P <: OauthPermission[C], TO <: OauthToken[RO, C]](
  val permissions: OauthResourceOwnerPermission[RO, C, P],
  val clientRepository: OauthClientRepository[C],
  val scopeRepository: OauthScopeRepository[SC],
  val codeFactory: OauthCodeFactory[CO, RO, C],
  val tokenFactory: OauthTokenFactory[TO, RO, C],
  val supportedResponseType: Seq[String] = OAuth.ResponseType.All
) extends Authorization[C, SC, CO, RO, P, TO]

object AuthorizationEndpoint {
  val logger:Logger = Logger(getClass)
}
