package controllers

import fr.njin.playoauth.as.endpoints._
import play.api._
import play.api.mvc._
import scala.concurrent.ExecutionContext
import play.api.mvc.Security.{AuthenticatedRequest, AuthenticatedBuilder}
import java.util.UUID
import fr.njin.playoauth.common.client.{BasicOauthScope, BasicOauthClient, BasicOauthClientInfo, OauthClient}
import scala.collection.mutable
import ExecutionContext.Implicits.global
import fr.njin.playoauth.common.OAuth

object Application extends Controller {

  object AuthzEndpointController extends AuthzEndpoint[BasicOauthClientInfo, BasicOauthClient, BasicOauthScope](
    new UUIDOauthClientFactory(),
    new InMemoryOauthClientRepository[BasicOauthClient](Map("1"  -> BasicOauthClient("1","secret", Seq(OAuth.ResponseType.Code, OAuth.ResponseType.Token), new BasicOauthClientInfo(Some(routes.Application.index.url))))),
    new InMemoryOauthScopeRepository[BasicOauthScope]()
  )

  case class User(username:String, authorizations:Map[OauthClient, String])

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
    AuthzEndpointController.authorize((authzRequest, oauthClient) => implicit request => {
      user.authorizations.find(_._1.id == oauthClient.id).fold(AuthzEndpointController.authzOk(UUID.randomUUID().toString)(authzRequest, oauthClient)(request)){ case (_, code) =>
        AuthzEndpointController.authzOk(code)(authzRequest, oauthClient)(request)
      }
    }).apply(request)
  }


  def login = Action { implicit request =>
    val u = User(UUID.randomUUID().toString, Map())
    users += (u.username -> u)
    Redirect(request.getQueryString("back").getOrElse(routes.Application.index.url))
      .withSession("username" -> u.username)
  }
  
}