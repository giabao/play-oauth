import fr.njin.playoauth.as.{OauthError, endpoints}
import fr.njin.playoauth.as.endpoints.ClientAuthentication
import fr.njin.playoauth.common.domain._
import fr.njin.playoauth.common.OAuth
import fr.njin.playoauth.common.request.TokenResponse
import java.util.{UUID, Date}
import org.specs2.specification.Scope
import play.api.libs.json.Writes
import play.api.mvc._
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration._
import scala.Some
import scala.Some

case class User(username:String, password:String, permissions:Map[BasicOauthClient, BasicOAuthPermission[BasicOauthClient]]) extends OauthResourceOwner {

}

object User extends OauthResourceOwnerPermission[User, BasicOauthClient, BasicOAuthPermission[BasicOauthClient]]{
  def apply(owner: User, client: BasicOauthClient): Future[Option[BasicOAuthPermission[BasicOauthClient]]] =
    Future.successful(owner.permissions.get(client))
}

trait Endpoint extends Scope {


  val timeout = 1.seconds

  lazy val user:Option[User] = None
  lazy val codes: Set[BasicOauthCode[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient]] = Set.empty
  lazy val tokens: Set[BasicOauthToken[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient]] = Set.empty

  trait ExampleClientAuthentication extends ClientAuthentication[BasicOauthClient] {
    import ExecutionContext.Implicits.global
    def authenticate(request: Request[AnyContentAsFormUrlEncoded]): Future[Either[Option[BasicOauthClient], OauthError]] =
      request.body.data.get(OAuth.OauthClientId).flatMap(_.headOption).map(id => repository.find(id))
        .fold(Future.successful[Either[Option[BasicOauthClient], OauthError]](Left(None)))(_.map(Left(_)))
  }

  lazy val repository = new InMemoryOauthClientRepository[BasicOauthClient]()
  lazy val scopeRepository = new InMemoryOauthScopeRepository[BasicOauthScope]()

  lazy val codeRepository = new InMemoryOauthCodeRepository[BasicOauthCode[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient], User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient](codes){
    def apply(owner: User, client: BasicOauthClient, redirectUri: Option[String], scopes: Option[Seq[String]]): Future[BasicOauthCode[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient]] = Future.successful {
      val code:BasicOauthCode[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient] = new BasicOauthCode(UUID.randomUUID().toString, owner, client, new Date().getTime, redirectUri = redirectUri, scopes = scopes)
      codes = codes + code
      code
    }
  }

  lazy val tokenRepository = new InMemoryOauthTokenRepository[BasicOauthToken[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient], User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient](tokens){
    def apply(owner: User, client: BasicOauthClient, redirectUri: Option[String], scopes: Option[Seq[String]]): Future[BasicOauthToken[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient]] = Future.successful{
      val token:BasicOauthToken[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient] = new BasicOauthToken(owner, client, UUID.randomUUID().toString, "example")
      tokens = tokens + token
      token
    }
  }

  lazy val authzEndpoint = new endpoints.AuthorizationEndpoint[BasicOauthClient, BasicOauthScope, BasicOauthCode[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient], User, BasicOAuthPermission[BasicOauthClient], BasicOauthToken[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient]](User, repository, scopeRepository, codeRepository, codeRepository, tokenRepository, tokenRepository)
  lazy val tokenEndpoint = new endpoints.TokenEndpoint[BasicOauthClient, BasicOauthScope, BasicOauthCode[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient], User, BasicOAuthPermission[BasicOauthClient], BasicOauthToken[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient]](repository, scopeRepository, codeRepository, codeRepository, tokenRepository, tokenRepository) with ExampleClientAuthentication
  lazy val tokenWithOnlyAuthorisationCodeEndpoint = new endpoints.TokenEndpoint[BasicOauthClient, BasicOauthScope, BasicOauthCode[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient], User, BasicOAuthPermission[BasicOauthClient], BasicOauthToken[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient]](repository, scopeRepository, codeRepository, codeRepository, tokenRepository, tokenRepository, Seq(OAuth.GrantType.AuthorizationCode)) with ExampleClientAuthentication

  def userByUsername: (String, String) => Future[Option[User]] = (u,p) => Future.successful(user.filter(user => user.username == u && user.password == p))
  def userOfClient: BasicOauthClient => Future[Option[User]] = client => Future.successful(Some(User(client.id, client.id, Map.empty)))

  def authz(implicit ec:ExecutionContext) = authzEndpoint.authorize(authzEndpoint.perform(r => user)(
    (ar,c) => r => Future.successful(Results.Unauthorized("")),
    (ar,c) => r => Future.successful(Results.Forbidden(""))
  ))(ec)

  def token(implicit ec:ExecutionContext, writes: Writes[TokenResponse], errorWrites: Writes[OauthError]) = tokenEndpoint.token(tokenEndpoint.perform(userByUsername, userOfClient))(ec, writes, errorWrites)
}

trait EndPointWithClients extends Endpoint {

  import Constants._

  val ownerAuthorizedClient = BasicOauthClient(ClientWithURI, ClientWithURI, Seq(OAuth.ResponseType.Code, OAuth.ResponseType.Token), Seq(OAuth.GrantType.AuthorizationCode), new BasicOauthClientInfo(Some(Seq(RedirectURI))))
  val ownerUnauthorizedClient = BasicOauthClient(ClientWithURIUnauthorized, ClientWithURIUnauthorized, Seq(OAuth.ResponseType.Code), Seq(OAuth.GrantType.AuthorizationCode), new BasicOauthClientInfo(Some(Seq(RedirectURI))))
  val clientWithCode = BasicOauthClient(ClientWithCode, ClientWithCode, Seq(OAuth.ResponseType.Code), Seq(OAuth.GrantType.AuthorizationCode), new BasicOauthClientInfo(Some(Seq(RedirectURI))))
  val anotherClientWithCode = BasicOauthClient(AnotherClientWithCode, AnotherClientWithCode, Seq(OAuth.ResponseType.Code), OAuth.GrantType.All, new BasicOauthClientInfo(Some(Seq(RedirectURI))))


  override lazy val user: Option[User] = Some(User(Username, Password,
    (Seq(
      new BasicOAuthPermission[BasicOauthClient](true, ownerAuthorizedClient, None, None),
      new BasicOAuthPermission[BasicOauthClient](false, ownerUnauthorizedClient, None, None),
      new BasicOAuthPermission[BasicOauthClient](false, BasicOauthClient(UnauthorizedClient, UnauthorizedClient, Seq(OAuth.ResponseType.Code), Seq(OAuth.GrantType.AuthorizationCode), new BasicOauthClientInfo(Some(Seq(RedirectURI)))), None, None),
      new BasicOAuthPermission[BasicOauthClient](true, clientWithCode, None, None),
      new BasicOAuthPermission[BasicOauthClient](true, anotherClientWithCode, None, None)
    ) map (p => p.client -> p)).toMap
  ))

  override lazy val codes = Set(
    new BasicOauthCode[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient](AuthorizationCode, user.get, clientWithCode, new Date().getTime),
    new BasicOauthCode[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient](ExpiredAuthorizationCode, user.get, clientWithCode, new Date().getTime - OAuth.MaximumLifetime.toMillis),
    new BasicOauthCode[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient](RevokedAuthorizationCode, user.get, clientWithCode, new Date().getTime, revoked = true),
    new BasicOauthCode[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient](AnotherAuthorizationCode, user.get, anotherClientWithCode, new Date().getTime, redirectUri = Some(RedirectURI))
  )

  override lazy val repository = new InMemoryOauthClientRepository[BasicOauthClient](
    (Seq(
      BasicOauthClient(ClientWithoutURI, ClientWithoutURI, Seq(OAuth.ResponseType.Code), Seq(OAuth.GrantType.AuthorizationCode)),
      ownerAuthorizedClient,
      ownerUnauthorizedClient,
      BasicOauthClient(ClientWithInvalidURI, ClientWithInvalidURI, Seq(), Seq(), new BasicOauthClientInfo(Some(Seq(InvalidURI)))),
      BasicOauthClient(UnauthorizedClient, UnauthorizedClient, Seq(), Seq(), new BasicOauthClientInfo(Some(Seq(RedirectURI)), authorized = false)),
      BasicOauthClient(ImplicitGrantClientWithURI, ImplicitGrantClientWithURI, Seq(OAuth.ResponseType.Token), Seq(OAuth.GrantType.ClientCredentials), new BasicOauthClientInfo(Some(Seq(RedirectURI)))),
      clientWithCode,
      anotherClientWithCode
    ) map (c => c.id -> c)).toMap
  )

  override lazy val scopeRepository: InMemoryOauthScopeRepository[BasicOauthScope] = new InMemoryOauthScopeRepository[BasicOauthScope](
    (Seq(
      new BasicOauthScope("scope1"),
      new BasicOauthScope("scope2")
    ) map (s => s.id -> s)).toMap
  )


}