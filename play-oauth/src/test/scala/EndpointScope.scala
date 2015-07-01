import java.time.Instant

import fr.njin.playoauth.as.{OauthError, endpoints}
import endpoints.{AuthorizationEndpoint, TokenEndpoint}
import fr.njin.playoauth.as.endpoints.ClientAuthentication
import fr.njin.playoauth.common.domain._
import fr.njin.playoauth.common.OAuth
import fr.njin.playoauth.common.request.TokenResponse
import java.util.UUID
import org.specs2.specification.Scope
import play.api.{Configuration, Environment}
import play.api.i18n.{DefaultLangs, MessagesApi, DefaultMessagesApi}
import play.api.libs.json.Writes
import play.api.mvc._
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration._
import ExecutionContext.Implicits.global
import com.sandinh.util.TimeUtil._

case class User(username:String,
                password:String,
                /** key is id of OauthClient */
                permissions:Map[String, BasicOAuthPermission])
    extends OauthResourceOwner {
  def id = username
}

trait Endpoint extends Scope {

  val timeout = 1.seconds

  lazy val userRep = new InMemoryOauthRORep[User]()
  lazy val codes: Set[BasicOauthCode] = Set.empty
  lazy val tokens: Set[BasicOauthToken] = Set.empty

  trait ExampleClientAuthentication extends ClientAuthentication[BasicOauthClient] {
    def authenticate(request: Request[AnyContentAsFormUrlEncoded]): Future[Either[Option[BasicOauthClient], OauthError]] =
      request.body.data.get(OAuth.OauthClientId).flatMap(_.headOption).map(id => repository.find(id))
        .fold(Future.successful[Either[Option[BasicOauthClient], OauthError]](Left(None)))(_.map(Left(_)))
  }

  lazy val repository = new InMemoryOauthClientRepository[BasicOauthClient]()
  lazy val allScopes = Seq.empty[String]

  lazy val codeRepository = new InMemoryOauthCodeRepository[BasicOauthCode](codes){
    def apply(ownerId: String, clientId: String, redirectUri: Option[String], scopes: Option[Seq[String]]): Future[BasicOauthCode] = Future.successful {
      val code = new BasicOauthCode(UUID.randomUUID().toString, ownerId, clientId, redirectUri = redirectUri, scopes = scopes)
      codes = codes + code
      code
    }
  }

  lazy val tokenRepository = new InMemoryOauthTokenRepository[BasicOauthToken](tokens){
    def apply(ownerId: String, clientId: String, redirectUri: Option[String], scopes: Option[Seq[String]]): Future[BasicOauthToken] = Future.successful{
      val token = new BasicOauthToken(UUID.randomUUID().toString, ownerId, clientId, "example")
      tokens = tokens + token
      token
    }
  }

  lazy val defaultMessagesApi: MessagesApi = {
    val env = Environment.simple()
    val cfg = Configuration.load(env)
    val langs = new DefaultLangs(cfg)
    new DefaultMessagesApi(env, cfg, langs)
  }

  object Permissions extends OauthResourceOwnerPermission[BasicOAuthPermission]{
    def apply(ownerId: String, clientId: String): Future[Option[BasicOAuthPermission]] =
      userRep.find(ownerId).map {
        case None => None
        case Some(user) => user.permissions.get(clientId)
      }
  }

  lazy val authzEndpoint = new AuthorizationEndpoint[BasicOauthClient, BasicOauthCode, User, BasicOAuthPermission, BasicOauthToken](
      Permissions, repository, allScopes, codeRepository, tokenRepository) {
    def messagesApi = defaultMessagesApi
  }
  lazy val tokenEndpoint = new TokenEndpoint[BasicOauthClient, BasicOauthCode, User, BasicOauthToken](
      repository, codeRepository, tokenRepository, tokenRepository) with ExampleClientAuthentication {
    def messagesApi = defaultMessagesApi
  }
  lazy val tokenWithOnlyAuthorisationCodeEndpoint = new TokenEndpoint[BasicOauthClient, BasicOauthCode, User, BasicOauthToken](
      repository, codeRepository, tokenRepository, tokenRepository, Seq(OAuth.GrantType.AuthorizationCode)) with ExampleClientAuthentication {
    def messagesApi = defaultMessagesApi
  }

  def userByUsername: (String, String) => Future[Option[User]] = (u,p) => userRep.find(u).map(_.filter(_.password == p))
  def userOfClient: BasicOauthClient => Future[Option[User]] = client => Future.successful(Some(User(client.id, client.id, Map.empty)))

  def authz(implicit ec:ExecutionContext) = authzEndpoint.authorize(r => userRep.find(Constants.Username))(
    (ar,c) => r => Future.successful(Results.Unauthorized),
    ro => (ar,c) => r => Future.successful(Results.Forbidden),
    error => Future.successful(Results.NotFound(error)),
    error => Future.successful(Results.BadRequest(error))
  )(ec)

  def token(implicit ec:ExecutionContext, writes: Writes[TokenResponse], errorWrites: Writes[OauthError]) = tokenEndpoint.token(userByUsername, userOfClient)(ec, writes, errorWrites)
}

trait EndPointWithClients extends Endpoint {

  import Constants._

  val ownerAuthorizedClient = new BasicOauthClient(ClientWithURI, ClientWithURI, Seq(OAuth.ResponseType.Code, OAuth.ResponseType.Token), Seq(OAuth.GrantType.AuthorizationCode), Some(Seq(RedirectURI)))
  val ownerUnauthorizedClient = new BasicOauthClient(ClientWithURIUnauthorized, ClientWithURIUnauthorized, Seq(OAuth.ResponseType.Code), Seq(OAuth.GrantType.AuthorizationCode), Some(Seq(RedirectURI)))
  val anotherOwnerUnauthorizedClient = new BasicOauthClient(UnauthorizedClient, UnauthorizedClient, Seq(OAuth.ResponseType.Code), Seq(OAuth.GrantType.AuthorizationCode), Some(Seq(RedirectURI)))
  val clientWithCode = new BasicOauthClient(ClientWithCode, ClientWithCode, Seq(OAuth.ResponseType.Code), Seq(OAuth.GrantType.AuthorizationCode), Some(Seq(RedirectURI)))
  val anotherClientWithCode = new BasicOauthClient(AnotherClientWithCode, AnotherClientWithCode, Seq(OAuth.ResponseType.Code), OAuth.GrantType.All, Some(Seq(RedirectURI)))


  override lazy val userRep = new InMemoryOauthRORep[User](Map(Username -> User(Username, Password,
    (Seq(
      new BasicOAuthPermission(true, ClientWithURI, None, None),
      new BasicOAuthPermission(false, ClientWithURIUnauthorized, None, None),
      new BasicOAuthPermission(false, UnauthorizedClient, None, None),
      new BasicOAuthPermission(true, ClientWithCode, None, None),
      new BasicOAuthPermission(true, AnotherClientWithCode, None, None)
    ) map (p => p.clientId -> p)).toMap
  )))

  override lazy val codes = Set(
    new BasicOauthCode(AuthorizationCode, Username, ClientWithCode),
    new BasicOauthCode(ExpiredAuthorizationCode, Username, ClientWithCode, Instant.now - OAuth.MaximumLifetime),
    new BasicOauthCode(RevokedAuthorizationCode, Username, ClientWithCode, revoked = true),
    new BasicOauthCode(AnotherAuthorizationCode, Username, AnotherClientWithCode, redirectUri = Some(RedirectURI))
  )

  override lazy val repository = new InMemoryOauthClientRepository[BasicOauthClient](
    (Seq(
      new BasicOauthClient(ClientWithoutURI, ClientWithoutURI, Seq(OAuth.ResponseType.Code), Seq(OAuth.GrantType.AuthorizationCode)),
      ownerAuthorizedClient,
      ownerUnauthorizedClient,
      anotherOwnerUnauthorizedClient,
      new BasicOauthClient(ClientWithInvalidURI, ClientWithInvalidURI, Seq(), Seq(), Some(Seq(InvalidURI))),
      new BasicOauthClient(BlockedClient, BlockedClient, Seq(OAuth.ResponseType.Code), Seq(OAuth.GrantType.AuthorizationCode), Some(Seq(RedirectURI)), authorized = false),
      new BasicOauthClient(ImplicitGrantClientWithURI, ImplicitGrantClientWithURI, Seq(OAuth.ResponseType.Token), Seq(OAuth.GrantType.ClientCredentials), Some(Seq(RedirectURI))),
      clientWithCode,
      anotherClientWithCode
    ) map (c => c.id -> c)).toMap
  )

  override lazy val allScopes = Seq("scope1", "scope2")
}
