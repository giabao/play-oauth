package domain

import fr.njin.playoauth.common.domain._
import scala.concurrent.{Future, ExecutionContext}
import scalikejdbc.async.AsyncDBSession
import models._
import scala.Some

class AppRepository(implicit val session: AsyncDBSession, val ec: ExecutionContext) extends OauthClientRepository[App] {

  def find(id: String): Future[Option[App]] = App.find(id)

}

class AuthTokenRepository(implicit val session: AsyncDBSession, val ec: ExecutionContext)
  extends OauthTokenRepository[AuthToken, User, Permission, App] {

  def find(value: String): Future[Option[AuthToken]] = AuthToken.findForValue(value)

  def findForRefreshToken(value: String): Future[Option[AuthToken]] = AuthToken.findForRefreshToken(value)

  def revoke(value: String): Future[Option[AuthToken]] = {
    (for {
      t <- AuthToken.findForValue(value)
      revoked <- t.get.revoke if t.isDefined
    } yield Some(revoked)).recover{ case _ => None }
  }
}

class AuthCodeRepository(implicit session:AsyncDBSession, ec: ExecutionContext) extends OauthCodeRepository[AuthCode, User, Permission, App] {

  def find(value: String): Future[Option[AuthCode]] = AuthCode.find(value, false)

  def revoke(value: String): Future[Option[AuthCode]] = {
    (for {
      t <- AuthCode.find(value)
      revoked <- t.get.revoke if t.isDefined
    } yield Some(revoked)).recover{ case _ => None }
  }
}

class InMemoryOauthScopeRepository[T <: OauthScope](var scopes:Map[String, T] = Map.empty[String, T], val defaultScopes:Option[Seq[T]] = None) extends OauthScopeRepository[T] {

  def defaults: Future[Option[Seq[T]]] = Future.successful(defaultScopes)

  def find(id: String): Future[Option[T]] = Future.successful(scopes.get(id))

  def find(id: String*): Future[Seq[(String,Option[T])]] = Future.successful(id.map(i => i -> scopes.get(i)))

  def save(scope: T): Future[T] = Future.successful {
    scopes += (scope.id -> scope)
    scope
  }

  def delete(scope: T): Future[Unit] = Future.successful {
    scopes -= scope.id
  }
}

