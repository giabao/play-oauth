import fr.njin.playoauth.common.domain._
import scala.concurrent.{Future, ExecutionContext}

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

class InMemoryOauthScopeRepository[T <: OauthScope](var scopes:Map[String, T] = Map.empty[String, T], val defaultScopes:Option[Seq[T]] = None) extends OauthScopeRepository[T] {

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

class InMemoryOauthTokenRepository[TO <: OauthToken](var tokens: Set[TO] = Set.empty[TO]) extends OauthTokenRepository[TO] {

  def find(value: String)(implicit ec: ExecutionContext): Future[Option[TO]] = Future.successful(tokens.find(_.accessToken == value))

  def save(token: TO)(implicit ec: ExecutionContext): Future[TO] = Future.successful {
    tokens = tokens + token
    token
  }

}