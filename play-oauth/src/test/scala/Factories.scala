import fr.njin.playoauth.common.domain._
import java.util.{Date, UUID}
import scala.concurrent.{Future, ExecutionContext}

class UUIDOauthClientFactory extends OauthClientFactory[BasicOauthClientInfo, BasicOauthClient] {
  def apply(allowedResponseType: Seq[String], allowedGrantType: Seq[String],info:BasicOauthClientInfo)(implicit ec: ExecutionContext): Future[BasicOauthClient] =
    Future.successful(BasicOauthClient(UUID.randomUUID().toString, UUID.randomUUID().toString, allowedResponseType, allowedGrantType, info))
}

class UUIDOauthCodeFactory[RO <: OauthResourceOwner[C, P], C <: OauthClient, P <: OauthPermission[C]] extends OauthCodeFactory[BasicOauthCode[RO, P, C], RO, P, C] {
  def apply(owner: RO, client: C, redirectUri: Option[String], scopes: Option[Seq[String]])(implicit ec:ExecutionContext): Future[BasicOauthCode[RO, P, C]] = Future.successful(new BasicOauthCode(UUID.randomUUID().toString, owner, client, new Date().getTime, redirectUri = redirectUri, scopes = scopes))
}

class UUIDOauthTokenFactory[RO <: OauthResourceOwner[C, P], C <: OauthClient, P <: OauthPermission[C]] extends OauthTokenFactory[BasicOauthToken, BasicOauthCode[RO, P, C], RO, P, C] {
  def apply(code: BasicOauthCode[RO, P, C], redirectUri: Option[String])(implicit ec: ExecutionContext): Future[BasicOauthToken] = Future.successful(new BasicOauthToken(UUID.randomUUID().toString, "example"))
}
