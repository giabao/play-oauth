package controllers

import fr.njin.playoauth.as.endpoints._
import play.api._
import play.api.mvc._
import scala.concurrent.{Future, ExecutionContext}
import play.api.mvc.Security.{AuthenticatedRequest, AuthenticatedBuilder}
import java.util.{Date, UUID}
import fr.njin.playoauth.common.domain._
import scala.collection.mutable
import ExecutionContext.Implicits.global
import fr.njin.playoauth.common.OAuth
import scala.Some

object Application extends Controller {


  object AuthzEndpointController extends AuthzEndpoint[BasicOauthClientInfo, BasicOauthClient, BasicOauthScope, BasicOauthCode[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient], User, BasicOAuthPermission[BasicOauthClient]](
    new UUIDOauthClientFactory(),
    new InMemoryOauthClientRepository[BasicOauthClient](Map("1"  -> BasicOauthClient("1","secret", Seq(OAuth.ResponseType.Code, OAuth.ResponseType.Token), OAuth.GrantType.All, new BasicOauthClientInfo(Some(routes.Application.index.url))))),
    new InMemoryOauthScopeRepository[BasicOauthScope](),
    new UUIDOauthCodeFactory[User, BasicOauthClient, BasicOAuthPermission[BasicOauthClient]](),
    new InMemoryOauthCodeRepository[BasicOauthCode[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient], User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient]()
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

class UUIDOauthClientFactory extends OauthClientFactory[BasicOauthClientInfo, BasicOauthClient] {
  def apply(allowedResponseType: Seq[String], allowedGrantType: Seq[String],info:BasicOauthClientInfo)(implicit ec: ExecutionContext): Future[BasicOauthClient] =
    Future.successful(BasicOauthClient(UUID.randomUUID().toString, UUID.randomUUID().toString, allowedResponseType, allowedGrantType, info))
}

class UUIDOauthCodeFactory[RO <: OauthResourceOwner[C, P], C <: OauthClient, P <: OauthPermission[C]] extends OauthCodeFactory[BasicOauthCode[RO, P, C], RO, P, C] {
  def apply(owner: RO, client: C, redirectUri: Option[String], scopes: Option[Seq[String]])(implicit ec:ExecutionContext): Future[BasicOauthCode[RO, P, C]] = Future.successful(new BasicOauthCode(UUID.randomUUID().toString, owner, client, new Date().getTime, redirectUri = redirectUri, scopes = scopes))
}

class InMemoryOauthClientRepository[T <: OauthClient](var clients:Map[String, T] = Map[String, T]()) extends OauthClientRepository[T] {

  def find(id: String)(implicit ec: ExecutionContext): Future[Option[T]] = Future.successful(clients.get(id))

  def save(client: T)(implicit ec: ExecutionContext): Future[T] = Future.successful {
    clients += (client.id -> client)
    client
  }

  def delete(client: T)(implicit ec: ExecutionContext): Future[Unit] = Future.successful {
    clients -= client.id
  }
}

class InMemoryOauthScopeRepository[T <: OauthScope](var scopes:Map[String, T] = Map[String, T](), val defaultScopes:Option[Seq[T]] = None) extends OauthScopeRepository[T] {

  def defaults(implicit ec: ExecutionContext): Future[Option[Seq[T]]] = Future.successful(defaultScopes)

  def find(id: String)(implicit ec: ExecutionContext): Future[Option[T]] = Future.successful(scopes.get(id))

  def find(id: String*)(implicit ec: ExecutionContext): Future[Seq[(String,Option[T])]] = Future.successful(id.map(i => i -> scopes.get(i)))

  def save(scope: T)(implicit ec: ExecutionContext): Future[T] = Future.successful {
    scopes += (scope.id -> scope)
    scope
  }

  def delete(scope: T)(implicit ec: ExecutionContext): Future[Unit] = Future.successful {
    scopes -= scope.id
  }
}

class InMemoryOauthCodeRepository[CO <: OauthCode[RO, P, C], RO <: OauthResourceOwner[C, P], P <: OauthPermission[C], C <: OauthClient](var codes: Set[CO] = Set.empty[CO]) extends OauthCodeRepository[CO, RO, P, C] {

  def find(value: String)(implicit ec: ExecutionContext): Future[Option[CO]] = Future.successful(codes.find(_.value == value))

  def save(code: CO)(implicit ec: ExecutionContext): Future[CO] = Future.successful {
    codes = codes + code
    code
  }

}