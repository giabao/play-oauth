package models

import java.time.Instant

import fr.njin.playoauth.common.OAuth
import fr.njin.playoauth.common.domain.OauthCode
import org.joda.time.DateTime
import scalikejdbc._
import scalikejdbc.async._

import scala.concurrent.Future

case class AuthCode(id: Long,
                    value: String,
                    permissionId: Long,
                    permission: Option[Permission] = None,
                    scopes: Option[Seq[String]],
                    redirectUri: Option[String],
                    createdAt: Instant,
                    revokedAt: Option[DateTime]) extends ShortenedNames with OauthCode {

  def owner: User = permission.flatMap(_.user).orNull
  def issueAt = createdAt
  def client: App = permission.flatMap(_.app).orNull
  def expiresIn = OAuth.MaximumLifetime
  def revoked: Boolean = revokedAt.isDefined

  def revoke(implicit session: AsyncDBSession, ctx: EC): Future[AuthCode] = AuthCode.revoke(this)

}

object AuthCode extends SQLSyntaxSupport[AuthCode] with ShortenedNames {

  override val columnNames: Seq[String] = Seq("id", "value", "permission_id", "scopes", "redirect_uri", "created_at", "revoked_at")

  def apply(ac: ResultName[AuthCode])(rs: WrappedResultSet): AuthCode = new AuthCode(
    id = rs.long(ac.id),
    value = rs.string(ac.value),
    permissionId = rs.long(ac.permissionId),
    scopes = rs.stringOpt(ac.scopes).map(_.split(" ")),
    redirectUri = rs.stringOpt(ac.redirectUri),
    createdAt = rs.jodaDateTime(ac.createdAt),
    revokedAt = rs.jodaDateTimeOpt(ac.revokedAt)
  )

  def apply(ac: SyntaxProvider[AuthCode])(rs: WrappedResultSet): AuthCode = apply(ac.resultName)(rs)

  def apply(ac: SyntaxProvider[AuthCode], p: SyntaxProvider[Permission])(rs: WrappedResultSet): AuthCode =
    apply(ac.resultName)(rs).copy(permission = Some(Permission(p)(rs)))

  def apply(ac: SyntaxProvider[AuthCode], p: SyntaxProvider[Permission],
            u: SyntaxProvider[User], a: SyntaxProvider[App])(rs: WrappedResultSet): AuthCode =
    apply(ac.resultName)(rs).copy(permission = Some(Permission(p, u, a)(rs)))

  lazy val ac = AuthCode.syntax("ac")
  private val u = User.u
  private val a = App.a
  private val p = Permission.p

  private def query[A](value: String, unRevokedOnly: Boolean = true): PagingSQLBuilder[A] =
    select
      .from(AuthCode as ac)
        .innerJoin(Permission as p).on(ac.permissionId, p.id)
        .innerJoin(User as u).on(p.userId, u.id)
        .innerJoin(App as a).on(p.appId, a.pid)
        .where
          .eq(ac.value, value)
          .and(sqls.toAndConditionOpt(
            if(unRevokedOnly) Some(sqls.isNull(ac.revokedAt)) else None
          ))
      .orderBy(ac.createdAt).desc


  def find(value: String, unRevokedOnly: Boolean = true)(implicit session: AsyncDBSession, cxt: EC): Future[Option[AuthCode]] = {
    withSQL {
      query(value)
    }.map(AuthCode(ac, p, u, a)).single().future()
  }

  def revoke(t: AuthCode, at:DateTime = DateTime.now())(implicit session: AsyncDBSession, ctx: EC): Future[AuthCode] = {
    withSQL {
      update(AuthCode).set(
        column.revokedAt -> at
      ).where
        .eq(column.id, t.id)
    }.update.future.map(_ => t.copy(revokedAt = Some(at)))
  }

  def create(value: String, permission: Permission, scopes: Option[Seq[String]], redirectUri: Option[String],
             createdAt: DateTime = DateTime.now(), revokedAt: Option[DateTime] = None)
            (implicit session: AsyncDBSession, ec: EC): Future[AuthCode] = {
    withSQL {
      insert.into(AuthCode).namedValues(
        column.value -> value,
        column.permissionId -> permission.id,
        column.scopes -> scopes.map(_.mkString(" ")),
        column.redirectUri -> redirectUri,
        column.createdAt -> createdAt,
        column.revokedAt -> revokedAt
      )
    }.updateAndReturnGeneratedKey().future.map(id =>
      AuthCode(
        id = id,
        value = value,
        permission = Some(permission),
        permissionId = permission.id,
        scopes = scopes,
        redirectUri = redirectUri,
        createdAt = createdAt,
        revokedAt = revokedAt
      )
    )
  }

}