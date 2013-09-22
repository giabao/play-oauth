import fr.njin.playoauth.as.endpoints
import fr.njin.playoauth.common.domain._
import fr.njin.playoauth.common.OAuth
import org.specs2.specification.Scope
import play.api.mvc.Results
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration._
import scala.Some

case class User(permissions:Map[BasicOauthClient, BasicOAuthPermission[BasicOauthClient]]) extends OauthResourceOwner[BasicOauthClient, BasicOAuthPermission[BasicOauthClient]] {
  def permission(client: BasicOauthClient): Option[BasicOAuthPermission[BasicOauthClient]] =
    permissions.get(client)
}

trait Endpoint extends Scope {

  import Constants._

  val timeout = 1.seconds

  def user:Option[User] = None

  lazy val factory = new UUIDOauthClientFactory()
  lazy val repository = new InMemoryOauthClientRepository[BasicOauthClient]()
  lazy val scopeRepository = new InMemoryOauthScopeRepository[BasicOauthScope]()
  lazy val codeFactory = new UUIDOauthCodeFactory[User, BasicOauthClient, BasicOAuthPermission[BasicOauthClient]]()
  lazy val codeRepository = new InMemoryOauthCodeRepository[BasicOauthCode[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient], User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient]()
  lazy val authzEndpoint = new endpoints.AuthzEndpoint[BasicOauthClientInfo, BasicOauthClient, BasicOauthScope, BasicOauthCode[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient], User, BasicOAuthPermission[BasicOauthClient]](factory, repository, scopeRepository, codeFactory, codeRepository)
  lazy val tokenEndpoint = new endpoints.TokenEndpoint[BasicOauthClientInfo, BasicOauthClient, BasicOauthScope, BasicOauthCode[User, BasicOAuthPermission[BasicOauthClient], BasicOauthClient], User, BasicOAuthPermission[BasicOauthClient]](factory, repository, scopeRepository, codeFactory, codeRepository)

  def authz(implicit ec:ExecutionContext) = authzEndpoint.authorize(authzEndpoint.perform(r => user)(
    (ar,c) => r => Future.successful(Results.Unauthorized("")),
    (ar,c) => r => Future.successful(Results.Forbidden(""))
  ))(ec)

  def token(implicit ec:ExecutionContext) = tokenEndpoint.token(tokenEndpoint.perform)(ec)
}

trait EndPointWithClients extends Endpoint {

  import Constants._

  val ownerAuthorizedClient = BasicOauthClient(ClientWithURI, ClientWithURI, Seq(OAuth.ResponseType.Code), Seq(OAuth.GrantType.AuthorizationCode), new BasicOauthClientInfo(Some(RedirectURI)))
  val ownerUnauthorizedClient = BasicOauthClient(ClientWithURIUnauthorized, ClientWithURIUnauthorized, Seq(OAuth.ResponseType.Code), Seq(OAuth.GrantType.AuthorizationCode), new BasicOauthClientInfo(Some(RedirectURI)))

  override def user: Option[User] = Some(User(Map(
    ownerAuthorizedClient -> new BasicOAuthPermission[BasicOauthClient](true, ownerAuthorizedClient, None, None),
    ownerUnauthorizedClient -> new BasicOAuthPermission[BasicOauthClient](false, ownerUnauthorizedClient, None, None)
  )))

  override lazy val repository = new InMemoryOauthClientRepository[BasicOauthClient](Map(
    ClientWithoutURI -> BasicOauthClient(ClientWithoutURI, ClientWithoutURI, Seq(OAuth.ResponseType.Code), Seq(OAuth.GrantType.AuthorizationCode)),
    ClientWithURI -> ownerAuthorizedClient,
    ClientWithURIUnauthorized -> ownerUnauthorizedClient,
    ClientWithInvalidURI -> BasicOauthClient(ClientWithInvalidURI, ClientWithInvalidURI, Seq(), Seq(), new BasicOauthClientInfo(Some(InvalidURI))),
    UnauthorizedClient -> BasicOauthClient(UnauthorizedClient, UnauthorizedClient, Seq(), Seq(), new BasicOauthClientInfo(Some(RedirectURI), authorized = false)),
    ImplicitGrantClientWithURI -> BasicOauthClient(ImplicitGrantClientWithURI, ImplicitGrantClientWithURI, Seq(OAuth.ResponseType.Token), Seq(OAuth.GrantType.ClientCredentials), new BasicOauthClientInfo(Some(RedirectURI))),
    ClientWithCode -> BasicOauthClient(ClientWithCode, ClientWithCode, Seq(OAuth.ResponseType.Code, OAuth.ResponseType.Token), Seq(OAuth.GrantType.AuthorizationCode), new BasicOauthClientInfo(Some(RedirectURI)))
  ))
  override lazy val scopeRepository: InMemoryOauthScopeRepository[BasicOauthScope] = new InMemoryOauthScopeRepository[BasicOauthScope](Map(
    "scope1" -> new BasicOauthScope("scope1"),
    "scope2" -> new BasicOauthScope("scope2")
  ))
}