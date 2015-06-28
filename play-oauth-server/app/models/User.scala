package models

import java.util.UUID

import fr.njin.playoauth.common.domain.{OauthResourceOwnerRepository, OauthResourceOwner}
import org.mindrot.jbcrypt.BCrypt
import play.api.libs.json._
import play.api.libs.functional.syntax._
import scalikejdbc._
import scalikejdbc.async.FutureImplicits._
import scalikejdbc.async._

import scala.concurrent.Future


/**
 * User: bathily
 * Date: 01/10/13
 */

case class User(id:String,
                email: String,
                password: String,
                firstName: String,
                lastName: String) extends ShortenedNames with OauthResourceOwner {

  val name = firstName+" "+lastName

  def save(implicit session: AsyncDBSession, ctx: EC): Future[User] = User.save(this)(session, ctx)
  def delete(implicit session: AsyncDBSession, ctx: EC): Future[Unit] = User.destroy(id)(session, ctx)

  def passwordMatch(candidate:String): Boolean =
    try {
      BCrypt.checkpw(candidate, password)
    } catch {
      case _: Throwable => false
    }
}

object User extends SQLSyntaxSupport[User] with ShortenedNames with OauthResourceOwnerRepository[User] {
  implicit val fmt = Json.format[User]

  override val tableName = "users"
  override val columnNames = Seq("id", "email", "password", "first_name", "last_name")

  def apply(c: SyntaxProvider[User])(rs: WrappedResultSet): User = apply(c.resultName)(rs)
  def apply(c: ResultName[User])(rs: WrappedResultSet): User = new User(
    id = rs.string(c.id),
    email = rs.string(c.email),
    password = rs.string(c.password),
    firstName = rs.string(c.firstName),
    lastName = rs.string(c.lastName)
  )

  // SyntaxProvider objects
  lazy val u = User.syntax("u")

  def find(id: String)(implicit session: AsyncDBSession, cxt: EC): Future[Option[User]] = {
    withSQL {
      select
        .from(User as u)
        .where.eq(u.id, id)
    }.map(User(u))
  }

  def findByEmail(email: String)(implicit session: AsyncDBSession, cxt: EC): Future[Option[User]] = {
    withSQL {
      select
        .from(User as u)
        .where.eq(u.email, email)
    }.map(User(u))
  }

  def create(email: String, password: String, firstName: String, lastName: String)
            (implicit session: AsyncDBSession, ctx: EC): Future[User] = {
    val hashpw: String = BCrypt.hashpw(password, BCrypt.gensalt())
    val id = UUID.randomUUID().toString

    withSQL {
      insert.into(User).namedValues(
        column.id -> id,
        column.email -> email,
        column.password -> hashpw,
        column.firstName -> firstName,
        column.lastName -> lastName
      )
    }.update().future.map(_ => User(id, email, hashpw, firstName, lastName))
  }

  def save(u: User)(implicit session: AsyncDBSession, ctx: EC): Future[User] = {
    withSQL {
      update(User).set(
        column.email -> u.email,
        column.password -> u.password,
        column.firstName -> u.firstName,
        column.lastName -> u.lastName
      ).where.eq(column.id, u.id)
    }.update.future.map(_ => u)
  }

  def destroy(id: String)(implicit session: AsyncDBSession, ctx: EC): Future[Unit] = {
    withSQL {
      delete.from(User).where.eq(column.id, id)
    }.update.future.map(_.toLong)
  }

}