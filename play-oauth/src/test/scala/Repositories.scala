import fr.njin.playoauth.common.domain._
import scala.concurrent.Future

class InMemoryOauthRORep[T <: OauthResourceOwner](val users: Map[String, T] = Map.empty[String, T]) extends OauthResourceOwnerRepository[T] {
  def find(id: String): Future[Option[T]] = Future.successful(users.get(id))
}

class InMemoryOauthClientRepository[T <: OauthClient](var clients:Map[String, T] = Map[String, T]()) extends OauthClientRepository[T] {

  def find(id: String)/**/: Future[Option[T]] = Future.successful(clients.get(id))

}

class InMemoryOauthScopeRepository[T <: OauthScope](var scopes:Map[String, T] = Map.empty[String, T]) extends OauthScopeRepository[T] {
  def find(id: String*): Future[Map[String, T]] = Future.successful(scopes)
}

abstract class InMemoryOauthCodeRepository[CO <: OauthCode](var codes: Set[CO] = Set.empty[CO])
  extends OauthCodeRepository[CO] with OauthCodeFactory[CO] {

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

abstract class InMemoryOauthTokenRepository[TO <: OauthToken](var tokens: Set[TO] = Set.empty[TO])
  extends OauthTokenRepository[TO] with OauthTokenFactory[TO] {

  import scala.concurrent.ExecutionContext.Implicits.global

  def find(value: String): Future[Option[TO]] = Future.successful(tokens.find(_.accessToken == value))

  def findForRefreshToken(value: String): Future[Option[TO]] = Future.successful(tokens.find(_.refreshToken.contains(value)))

  def revoke(value: String): Future[Option[TO]] = find(value).map(_.map { token =>
    tokens = tokens - token
    token
  })
}