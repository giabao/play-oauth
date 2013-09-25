import fr.njin.playoauth.as.endpoints
import fr.njin.playoauth.as.endpoints.{ClientAuthentication, OauthError}
import fr.njin.playoauth.common.domain._
import fr.njin.playoauth.common.OAuth
import java.util.Date
import org.specs2.specification.Scope
import play.api.libs.json.Writes
import play.api.mvc.{AnyContent, Request, Results}
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration._
import scala.Some

case class User(permissions:Map[BasicOauthClient, BasicOAuthPermission[BasicOauthClient]]) extends OauthResourceOwner[BasicOauthClient, BasicOAuthPermission[BasicOauthClient]] {
  def permission(client: BasicOauthClient): Option[BasicOAuthPermission[BasicOauthClient]] =
    permissions.get(client)
}

trait Endpoint extends Scope {


  val timeout = 1.seconds

  lazy val user:Option[User] = None

  trait ExampleClientAuthentication extends ClientAuthentication[BasicOauthClient] {
    def authenticate(request: Request[AnyContent])(implicit ec: ExecutionContext): Future[Either[Option[BasicOauthClient], OauthError]] =
      request.getQueryString(OAuth.OauthClientId).map(id => repository.find(id)).fold(Future.successful[Either[Option[BasicOauthClient], OauthError]](Left(None)))(_.map(Left(_)))
  }

  lazy val factory = new UUIDOauthClientFactory()
  lazy val repository = new InMemoryOauthClientRepository[BasicOauthClient]()
  lazy val scopeRepository = new InMemoryOauthScopeRepository[BasicOauthScope]()
  lazy val codeFactory = new UUIDOauthCodeFactory[User, BasicOauthClient, BasicOAuthPermission[BasicOauthClient]]()
  lazy val codeRepository = new InMemoryOauthCodeRepository[BasicOauthCode[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient], User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient]()
  lazy val tokenFactory = new UUIDOauthTokenFactory[User, BasicOauthClient, BasicOAuthPermission[BasicOauthClient]]()
  lazy val tokenRepository = new InMemoryOauthTokenRepository[BasicOauthToken]()
  lazy val authzEndpoint = new endpoints.AuthzEndpoint[BasicOauthClientInfo, BasicOauthClient, BasicOauthScope, BasicOauthCode[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient], User, BasicOAuthPermission[BasicOauthClient], BasicOauthToken](factory, repository, scopeRepository, codeFactory, codeRepository, tokenFactory, tokenRepository)
  lazy val tokenEndpoint = new endpoints.TokenEndpoint[BasicOauthClientInfo, BasicOauthClient, BasicOauthScope, BasicOauthCode[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient], User, BasicOAuthPermission[BasicOauthClient], BasicOauthToken](factory, repository, scopeRepository, codeFactory, codeRepository, tokenFactory, tokenRepository) with ExampleClientAuthentication
  lazy val tokenWithOnlyAuthorisationCodeEndpoint = new endpoints.TokenEndpoint[BasicOauthClientInfo, BasicOauthClient, BasicOauthScope, BasicOauthCode[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient], User, BasicOAuthPermission[BasicOauthClient], BasicOauthToken](factory, repository, scopeRepository, codeFactory, codeRepository, tokenFactory, tokenRepository, Seq(OAuth.GrantType.AuthorizationCode)) with ExampleClientAuthentication

  def authz(implicit ec:ExecutionContext) = authzEndpoint.authorize(authzEndpoint.perform(r => user)(
    (ar,c) => r => Future.successful(Results.Unauthorized("")),
    (ar,c) => r => Future.successful(Results.Forbidden(""))
  ))(ec)

  def token(implicit ec:ExecutionContext, writes: Writes[BasicOauthToken], errorWrites: Writes[OauthError]) = tokenEndpoint.token(tokenEndpoint.perform)(ec, writes, errorWrites)
}

trait EndPointWithClients extends Endpoint {

  import Constants._

  val ownerAuthorizedClient = BasicOauthClient(ClientWithURI, ClientWithURI, Seq(OAuth.ResponseType.Code, OAuth.ResponseType.Token), Seq(OAuth.GrantType.AuthorizationCode), new BasicOauthClientInfo(Some(Seq(RedirectURI))))
  val ownerUnauthorizedClient = BasicOauthClient(ClientWithURIUnauthorized, ClientWithURIUnauthorized, Seq(OAuth.ResponseType.Code), Seq(OAuth.GrantType.AuthorizationCode), new BasicOauthClientInfo(Some(Seq(RedirectURI))))
  val clientWithCode = BasicOauthClient(ClientWithCode, ClientWithCode, Seq(OAuth.ResponseType.Code), Seq(OAuth.GrantType.AuthorizationCode), new BasicOauthClientInfo(Some(Seq(RedirectURI))))
  val anotherClientWithCode = BasicOauthClient(AnotherClientWithCode, AnotherClientWithCode, Seq(OAuth.ResponseType.Code), OAuth.GrantType.All, new BasicOauthClientInfo(Some(Seq(RedirectURI))))


  override lazy val user: Option[User] = Some(User(
    (Seq(
      new BasicOAuthPermission[BasicOauthClient](true, ownerAuthorizedClient, None, None),
      new BasicOAuthPermission[BasicOauthClient](false, ownerUnauthorizedClient, None, None),
      new BasicOAuthPermission[BasicOauthClient](false, BasicOauthClient(UnauthorizedClient, UnauthorizedClient, Seq(OAuth.ResponseType.Code), Seq(OAuth.GrantType.AuthorizationCode), new BasicOauthClientInfo(Some(Seq(RedirectURI)))), None, None),
      new BasicOAuthPermission[BasicOauthClient](true, clientWithCode, None, None),
      new BasicOAuthPermission[BasicOauthClient](true, anotherClientWithCode, None, None)
    ) map (p => p.client -> p)).toMap
  ))

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

  override lazy val codeRepository = new InMemoryOauthCodeRepository[BasicOauthCode[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient], User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient](Set(
    new BasicOauthCode[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient](AuthorizationCode, user.get, clientWithCode, new Date().getTime),
    new BasicOauthCode[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient](ExpiredAuthorizationCode, user.get, clientWithCode, new Date().getTime - OAuth.MaximumLifetime.toMillis),
    new BasicOauthCode[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient](RevokedAuthorizationCode, user.get, clientWithCode, new Date().getTime, revokedAt = Some(new Date().getTime)),
    new BasicOauthCode[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient](AnotherAuthorizationCode, user.get, anotherClientWithCode, new Date().getTime, redirectUri = Some(RedirectURI))
  ))
}