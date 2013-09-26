package controllers

import fr.njin.playoauth.as.endpoints._
import play.api.mvc._
import scala.concurrent.{Future, ExecutionContext}
import play.api.mvc.Security.AuthenticatedBuilder
import java.util.UUID
import fr.njin.playoauth.common.domain._
import scala.collection.mutable
import ExecutionContext.Implicits.global
import fr.njin.playoauth.common.OAuth
import domain._
import scala.Some

object Application extends Controller {


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

  val users = mutable.HashMap[String, User]()

  object Authenticated extends AuthenticatedBuilder[User](
    request => request.session.get("username").flatMap(users.get),
    request => Redirect(routes.Application.login.url, Map("back" -> Seq(request.uri)))) {
  }

  def index = Authenticated {
    Ok("ok")
  }

  def authz = Authenticated.async { implicit request =>
    val user = request.user
    AuthzEndpointController.authorize(AuthzEndpointController.perform(r => Some(user))(
        (ar, c) => implicit r => Future.successful(InternalServerError("")),
        (ar, c) => implicit r => {
          user.authorizations + (c -> new BasicOAuthPermission[BasicOauthClient](true, c, ar.scope, ar.redirectUri))
          Future.successful(Ok(""))
        }
    )).apply(request)
  }


  def login = Action { implicit request =>
    val u = User(UUID.randomUUID().toString, Map())
    users += (u.username -> u)
    Redirect(request.getQueryString("back").getOrElse(routes.Application.index.url))
      .withSession("username" -> u.username)
  }
}