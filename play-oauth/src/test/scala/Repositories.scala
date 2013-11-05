import fr.njin.playoauth.common.domain._
import scala.concurrent.Future

class InMemoryOauthClientRepository[T <: OauthClient](var clients:Map[String, T] = Map[String, T]()) extends OauthClientRepository[T] {

  def find(id: String)/**/: Future[Option[T]] = Future.successful(clients.get(id))

}

class InMemoryOauthScopeRepository[T <: OauthScope](var scopes:Map[String, T] = Map.empty[String, T]) extends OauthScopeRepository[T] {
  def find(id: String*): Future[Map[String, T]] = Future.successful(scopes)
}

abstract class InMemoryOauthCodeRepository[CO <: OauthCode[RO, C], RO <: OauthResourceOwner, C <: OauthClient](var codes: Set[CO] = Set.empty[CO])
  extends OauthCodeRepository[CO, RO, C] with OauthCodeFactory[CO, RO, C] {

  import scala.concurrent.ExecutionContext.Implicits.global

  def find(value: String): Future[Option[CO]] = Future.successful(codes.find(_.value == value))

  def save(code: CO): Future[CO] = Future.successful {
    codes = codes + code
    code
  }

  def revoke(value: String): Future[Option[CO]] = find(value).map(_.map { code =>
    codes = codes - code
    code
  })
}

abstract class InMemoryOauthTokenRepository[TO <: OauthToken[RO, C], RO <: OauthResourceOwner, C <: OauthClient](var tokens: Set[TO] = Set.empty[TO])
  extends OauthTokenRepository[TO, RO, C] with OauthTokenFactory[TO, RO, C] {

  import scala.concurrent.ExecutionContext.Implicits.global

  def find(value: String): Future[Option[TO]] = Future.successful(tokens.find(_.accessToken == value))

  def findForRefreshToken(value: String): Future[Option[TO]] = Future.successful(tokens.find(_.refreshToken == Some(value)))

  def revoke(value: String): Future[Option[TO]] = find(value).map(_.map { token =>
    tokens = tokens - token
    token
  })
}