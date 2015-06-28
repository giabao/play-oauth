package models

import fr.njin.playoauth.common.domain.OauthToken
import org.joda.time.DateTime
import play.api.libs.json._
import play.api.libs.functional.syntax._
import scalikejdbc._
import scalikejdbc.async._

import scala.concurrent.Future
import scala.concurrent.duration.{FiniteDuration, DurationInt}
import scala.language.postfixOps

case class AuthToken(id:Long,
                     value: String,
                     tokenType: String,
                     permissionId: Long,
                     permission: Option[Permission] = None,
                     lifetime: FiniteDuration,
                     revokedAt: Option[DateTime],
                     refreshToken: Option[String] = None,
                     createdAt: DateTime) extends OauthToken with ShortenedNames{
  def client: App = permission.flatMap(_.app).orNull
  def owner: User = permission.flatMap(_.user).orNull
  def revoked: Boolean = permission.exists(_.revokedAt.isDefined) || revokedAt.isDefined
  def scopes: Option[Seq[String]] = permission.flatMap(_.scopes)
  def expiresIn = lifetime

  def revoke(implicit session: AsyncDBSession, ctx: EC): Future[AuthToken] = AuthToken.revoke(this)

  def issueAt: Long = createdAt.getMillis / 1000
}

object AuthToken extends SQLSyntaxSupport[AuthToken] with ShortenedNames {
  implicit val fmt = Json.format[AuthToken]

  override val columnNames: Seq[String] = Seq("id", "value", "token_type", "permission_id", "lifetime", "revoked_at", "refresh_token", "created_at")

  def apply(t: ResultName[AuthToken])(rs: WrappedResultSet): AuthToken = new AuthToken(
    id = rs.long(t.id),
    value = rs.string(t.value),
    tokenType = rs.string(t.tokenType),
    permissionId = rs.long(t.permissionId),
    lifetime = rs.long(t.lifetime),
    revokedAt = rs.jodaDateTimeOpt(t.revokedAt),
    refreshToken = rs.stringOpt(t.refreshToken),
    createdAt = rs.jodaDateTime(t.createdAt).toDateTime
  )

  def apply(t: SyntaxProvider[AuthToken])(rs: WrappedResultSet): AuthToken = apply(t.resultName)(rs)

  def apply(t: SyntaxProvider[AuthToken], p: SyntaxProvider[Permission])(rs: WrappedResultSet): AuthToken =
    apply(t)(rs).copy(permission = Some(Permission(p)(rs)))

  def apply(t: SyntaxProvider[AuthToken], p: SyntaxProvider[Permission], u: SyntaxProvider[User], a: SyntaxProvider[App])(rs: WrappedResultSet): AuthToken =
    apply(t)(rs).copy(permission = Some(Permission(p, u, a)(rs)))

  lazy val t = AuthToken.syntax("t")
  private val p = Permission.p
  private val u = User.u
  private val a = App.a

  def create(permission: Permission,
             value: String, tokenType: String, refreshToken: String,
             lifetime: Long = (10 days).toMillis,
             revokedAt: Option[DateTime] = None, createdAt: DateTime = DateTime.now())(implicit session: AsyncDBSession, ec: EC): Future[AuthToken] = {
    withSQL {
      insert.into(AuthToken).namedValues(
        column.value -> value,
        column.tokenType -> tokenType,
        column.permissionId -> permission.id,
        column.lifetime -> lifetime,
        column.revokedAt -> revokedAt,
        column.createdAt -> createdAt,
        column.refreshToken -> refreshToken
      )
    }.updateAndReturnGeneratedKey().future().map(id =>
      AuthToken(
        id = id,
        value = value,
        tokenType = tokenType,
        permissionId = permission.id,
        permission = Some(permission),
        lifetime = lifetime,
        revokedAt = revokedAt,
        createdAt = createdAt,
        refreshToken = Some(refreshToken)
      )
    )
  }

  def revoke(t: AuthToken, at:DateTime = DateTime.now())(implicit session: AsyncDBSession, ctx: EC): Future[AuthToken] = {
    withSQL {
      update(AuthToken).set(
        column.revokedAt -> at
      ).where
        .eq(column.id, t.id)
    }.update.future.map(_ => t.copy(revokedAt = Some(at)))
  }

  def findForValue(value: String)(implicit session: AsyncDBSession, cxt: EC): Future[Option[AuthToken]] =
    withSQL {
      select
        .from(AuthToken as t)
          .innerJoin(Permission as p).on(t.permissionId, p.id)
          .innerJoin(User as u).on(p.userId, u.id)
          .innerJoin(App as a).on(p.appId, a.pid)
        .where
          .eq(t.value, value)
    }.map(AuthToken(t, p, u, a)).single().future()

  def findForRefreshToken(value: String)(implicit session: AsyncDBSession, cxt: EC): Future[Option[AuthToken]] =
    withSQL {
      select
        .from(AuthToken as t)
          .innerJoin(Permission as p).on(t.permissionId, p.id)
        .where
          .eq(t.refreshToken, value)
    }.map(AuthToken(t, p)).single().future()
}