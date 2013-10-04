package controllers

import play.api.mvc.Controller

/**
 * User: bathily
 * Date: 03/10/13
 */
object Authorization extends Controller {

  def authz = TODO /*Authenticated.async { implicit request =>
    val user = request.user
    AuthzEndpointController.authorize(AuthzEndpointController.perform(r => Some(user))(
        (ar, c) => implicit r => Future.successful(InternalServerError("")),
        (ar, c) => implicit r => {
          user.authorizations + (c -> new BasicOAuthPermission[BasicOauthClient](true, c, ar.scope, ar.redirectUri))
          Future.successful(Ok(""))
        }
    )).apply(request)
  }*/

}

/*
  object AuthzEndpointController extends AuthzEndpoint[BasicOauthClientInfo, BasicOauthClient, BasicOauthScope, BasicOauthCode[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient], User, BasicOAuthPermission[BasicOauthClient], BasicOauthToken[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient]](
    new UUIDOauthClientFactory(),
    new InMemoryOauthClientRepository[BasicOauthClient](),
    new InMemoryOauthScopeRepository[BasicOauthScope](),
    new UUIDOauthCodeFactory[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient](),
    new InMemoryOauthCodeRepository[BasicOauthCode[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient], User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient](),
    new UUIDOauthTokenFactory[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient](),
    new InMemoryOauthTokenRepository[BasicOauthToken[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient], User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient]()
  )

  case class User(username:String, authorizations:Map[BasicOauthClient, BasicOAuthPermission[BasicOauthClient]])
    extends OauthResourceOwner[BasicOauthClient, BasicOAuthPermission[BasicOauthClient]]{
    def permission(client: BasicOauthClient): Option[BasicOAuthPermission[BasicOauthClient]] =
      authorizations.get(client)
  }
  */
