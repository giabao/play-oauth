package fr.njin.playoauth.as.endpoints

import fr.njin.playoauth.as.OauthError
import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future}
import play.api.data.Form
import fr.njin.playoauth.common.OAuth
import play.api.i18n.{I18nSupport, Messages}
import fr.njin.playoauth.common.domain._
import Results._
import play.api.http.Status._
import fr.njin.playoauth.as.OauthError._
import fr.njin.playoauth.common.request.AuthzRequest
import Requests._
import play.api.Logger
import fr.njin.playoauth.Utils

trait AuthzReqValidator[C <: OauthClient] extends I18nSupport {
  def supportedResponseType: Seq[String]

  def allScopes: Seq[String]

  /**
   * Alias for Authorization request validation function
   *
   *
   * A validator takes an authorization request and a client then return the eventual errors.
   */
  private[this] type Validator =  (AuthzRequest, C) => Messages => Option[OauthError]

  /**
   * Validates response type of the request.
   *
   * If [[supportedResponseType]] does not contain the requested response type,
   * a [[fr.njin.playoauth.as.OauthError.unsupportedResponseTypeError]] is returned
   */
  private[this] val responseTypeCodeValidator:Validator = (authzRequest, client) => messages =>
    if(supportedResponseType.contains(authzRequest.responseType)) {
      None
    } else {
      Some(unsupportedResponseTypeError())
    }

  /**
   * Validates the scope of the request.
   *
   * All values of the requested scopes must be found by the scopeRepository
   * otherwise a [[fr.njin.playoauth.as.OauthError.invalidScopeError]] is returned
   * with the missing scopes
   */
  private[this] val scopeValidator:Validator = (authzRequest, client) => messages =>
    authzRequest.scopes.map { reqScopes =>
      val errors = reqScopes.filterNot(allScopes.contains)
      if(errors.isEmpty) None
      else {
        val desc = messages(OAuth.ErrorInvalidScope, errors.mkString(" "))
        Some(invalidScopeError(Some(desc)))
      }
    }.getOrElse(None)

  /**
   * Validates the access of the client
   *
   * If the client is not authorized a [[fr.njin.playoauth.as.OauthError.accessDeniedError]] is returned
   */
  private[this] val clientAuthorizedValidator:Validator = (authzRequest, client) => messages =>
    if(client.authorized) {
      None
    } else {
      Some(accessDeniedError())
    }

  /**
   * Validates the client's response type
   *
   * If [[fr.njin.playoauth.common.domain.OauthClient.allowedResponseType]] does not contain the requested response type,
   * a [[fr.njin.playoauth.as.OauthError.unauthorizedClientError]] is returned
   */
  private[this] val clientResponseTypeValidator:Validator = (authzRequest, client) => messages =>
    if(client.allowedResponseType.contains(authzRequest.responseType)) {
      None
    } else {
      val desc = messages(OAuth.ErrorUnauthorizedResponseType, authzRequest.responseType)
      Some(unauthorizedClientError(Some(desc)))
    }

  /**
   * All validators that will be tested for the request
   */
  private[this] val authzValidator = List(
    responseTypeCodeValidator,
    scopeValidator,
    clientAuthorizedValidator,
    clientResponseTypeValidator
  )

  def validateAuthzRequest(authzRequest: AuthzRequest, client: C, req: RequestHeader) =
    authzValidator.map(_(authzRequest, client)(request2Messages(req)))
      .find(_.isDefined)
}

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
 * @tparam CO Code type
 * @tparam RO Resource owner type
 * @tparam P Permission type
 * @tparam TO Token type
 */
trait Authorization[C <: OauthClient, CO <: OauthCode, RO <: OauthResourceOwner,
                    P <: OauthPermission, TO <: OauthToken] extends AuthzReqValidator[C] {

  val logger:Logger = AuthorizationEndpoint.logger

  def permissions: OauthResourceOwnerPermission[P]
  def clientRepository: OauthClientRepository[C]
  def codeFactory: OauthCodeFactory[CO]
  def tokenFactory: OauthTokenFactory[TO]

  type AuthzCallback = (AuthzRequest, C) => RequestHeader => Future[Result]

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
   * @param onNotFound called if the client is not found 
   * @param onBadRequest called if the redirect url or client id is invalid 
   * @param toQuery a function which builds the result query
   * @param request the http request header
   * @param ec an execution context
   * @return the result of the authorization
   */
  def onError(f:Form[AuthzRequest], toQuery: Form[AuthzRequest] => Map[String, Seq[String]])
             (onNotFound: String => Future[Result])
             (onBadRequest: String => Future[Result])
             (implicit request:RequestHeader, ec:ExecutionContext): Future[Result] =
    f.error(OAuth.OauthClientId).map(e => onBadRequest(Messages(OAuth.ErrorClientMissing)))
      .orElse(f.error(OAuth.OauthRedirectUri).map(e => {
        println(e.args)
        onBadRequest(Messages(OAuth.ErrorRedirectURIInvalid, e.args))
      })).getOrElse {
        val id = f(OAuth.OauthClientId).value.get
        clientRepository.find(id).flatMap(_.fold(onNotFound(Messages(OAuth.ErrorClientNotFound, id))) { client =>

          def responseTo(uri: Option[String]): Future[Result] =  uri.fold {
            onBadRequest(Messages(OAuth.ErrorRedirectURIMissing))
          } { url =>
            Future.successful(Redirect(url, toQuery(f), FOUND))
          }

          (f(OAuth.OauthResponseType).value, f(OAuth.OauthRedirectUri).value) match {
            case ((Some(OAuth.ResponseType.Token), Some(uri))) =>
              if (client.redirectUris.exists(_.contains(uri))) {
                responseTo(Some(uri))
              } else {
                onBadRequest(Messages(OAuth.ErrorRedirectURINotMatch, uri))
              }
            case ((Some(OAuth.ResponseType.Code)), uri) => responseTo(uri.orElse(client.redirectUri))
            case _ => responseTo(client.redirectUri)
          }

      })
    }

  def onFormError(f:Form[AuthzRequest])
                 (onNotFound: String => Future[Result])
                 (onBadRequest: String => Future[Result])
                 (implicit request:RequestHeader, ec:ExecutionContext): Future[Result] =
    onError(f, errorToQuery)(onNotFound)(onBadRequest)

  def onServerError(f:Form[AuthzRequest], error: Throwable)
                   (onNotFound: String => Future[Result])
                   (onBadRequest: String => Future[Result])
                   (implicit request:RequestHeader, ec:ExecutionContext): Future[Result] = {
    logger.error("Error occurred while authorizing", error)
    onError(f, f => queryWithState(serverError() ,f(OAuth.OauthState).value))(onNotFound)(onBadRequest)
  }

  /**
   * Request handler
   *
   * This method is called when the request is successfully bound.
   * It search the client and really addresses the request with the callback
   *
   * @param authzRequest the request
   * @param f the callback. Called when all validations pass
   * @param onNotFound called when the client is not found
   * @param onBadRequest called when redirect uri is missing
   * @param request the http request header
   * @param ec an execution context
   * @return the result of the authorization
   */
  def onAuthzRequest(authzRequest: AuthzRequest)
                    (onNotFound: String => Future[Result])
                    (onBadRequest: String => Future[Result])
                    (f: AuthzCallback)
                    (implicit request:RequestHeader, ec:ExecutionContext): Future[Result] =
    clientRepository.find(authzRequest.clientId).flatMap{
      _.fold(onNotFound(Messages(OAuth.ErrorClientNotFound, authzRequest.clientId))){ client =>
        authzRequest.redirectUri.orElse(client.redirectUri).fold(
          onBadRequest(Messages(OAuth.ErrorRedirectURIMissing))) { url =>
            validateAuthzRequest(authzRequest, client, request) match {
            case Some(e) => Future.successful(Redirect(url, queryWithState(e.get, authzRequest.state), FOUND))
            case _ => f(authzRequest, client)(request)
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
   *
   * @return the result of the authorization
   */
  def authorize(owner:RequestHeader => Future[Option[RO]])
               (onUnauthenticated:AuthzCallback,
                onUnauthorized:AuthzCallback,
                onNotFound: String => Future[Result],
                onBadRequest: String => Future[Result])
               (implicit ec:ExecutionContext): RequestHeader => Future[Result] =
    authorize(perform(owner)(onUnauthenticated, onUnauthorized))(onNotFound)(onBadRequest)

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
  def authorize(f:AuthzCallback)
               (onNotFound: String => Future[Result])
               (onBadRequest: String => Future[Result])
               (implicit ec:ExecutionContext): RequestHeader => Future[Result] =
    implicit request => {
      val query = request.queryString
      val form = authorizeRequestForm.bindFromRequest(query)

      Option(query.filter(_._2.length > 1)).filterNot(_.isEmpty).map { params =>
        form.withGlobalError(Messages(OAuth.ErrorMultipleParameters, params.keySet.mkString(",")))
      }.getOrElse(form).fold(formWithErrors => {
        onFormError(formWithErrors)(onNotFound)(onBadRequest)
      }, onAuthzRequest(_)(onNotFound)(onBadRequest)(f)).recoverWith {
        case t:Throwable => onServerError(form, t)(onNotFound)(onBadRequest)
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
  def perform(owner:RequestHeader => Future[Option[RO]])
             (onUnauthenticated:AuthzCallback,
              onUnauthorized:AuthzCallback)
             (implicit ec:ExecutionContext): AuthzCallback =
    (authzRequest, oauthClient) => implicit request => {
      owner(request).flatMap(_.fold(onUnauthenticated(authzRequest, oauthClient)(request)) { resourceOwner =>
        permissions(resourceOwner.id, oauthClient.id).flatMap(_.fold(onUnauthorized(authzRequest, oauthClient)(request)) { permission =>
          if(permission.authorized(authzRequest)) {
            codeFactory(resourceOwner.id, oauthClient.id, authzRequest.redirectUri, authzRequest.scopes)
              .flatMap { code =>
                authzRequest.responseType match {
                  case OAuth.ResponseType.Code =>
                    authzAccept(code)(authzRequest, oauthClient)(request)
                  case OAuth.ResponseType.Token =>
                    tokenFactory(code.ownerId, code.clientId, authzRequest.redirectUri, code.scopes).flatMap { token =>
                      authzAccept(token)(authzRequest, oauthClient)(request)
                    }
                }
              }
          } else {
            authzDeny(authzRequest, oauthClient)(request)
          }
        })
      })
    }

  /**
   * Builds the success result for the created code
   *
   * <b>Caution : make sure that all validations passed before calling this method</b>
   *
   * @param code the newly created code
   * @return the authorization result
   */
  def authzAccept(code:CO): AuthzCallback =
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
  def authzAccept(token:TO): AuthzCallback =
    (authzRequest, oauthClient) => implicit request => {
      import Utils.toUrlFragment

      val url = authzRequest.redirectUri.orElse( oauthClient.redirectUri).get
      Future.successful(Redirect(url + toUrlFragment(
        Map(
          OAuth.OauthAccessToken -> Seq(token.accessToken),
          OAuth.OauthTokenType -> Seq(token.tokenType),
          OAuth.OauthExpiresIn -> Seq(token.expiresIn.toSeconds.toString)
        ) ++ token.scopes.map(s => OAuth.OauthScope -> Seq(s.mkString(" ")))
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
  def authzDeny: AuthzCallback =
    (authzRequest, oauthClient) => implicit request => {
      val url = oauthClient.redirectUri.orElse(authzRequest.redirectUri).get
      Future.successful(Redirect(url, queryWithState(accessDeniedError(), authzRequest.state), FOUND))
    }
}

abstract class AuthorizationEndpoint[C <: OauthClient, CO <: OauthCode, RO <: OauthResourceOwner,
                                     P <: OauthPermission, TO <: OauthToken](
  val permissions: OauthResourceOwnerPermission[P],
  val clientRepository: OauthClientRepository[C],
  val allScopes: Seq[String],
  val codeFactory: OauthCodeFactory[CO],
  val tokenFactory: OauthTokenFactory[TO],
  val supportedResponseType: Seq[String] = OAuth.ResponseType.All
) extends Authorization[C, CO, RO, P, TO]

object AuthorizationEndpoint {
  val logger:Logger = Logger(getClass)
}
