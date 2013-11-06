package models

import play.api.libs.json._
import play.api.libs.functional.syntax._
import org.joda.time.DateTime
import scalikejdbc._, async._, SQLInterpolation._
import scala.concurrent.Future
import fr.njin.playoauth.common.domain.OauthPermission
import fr.njin.playoauth.common.request.AuthzRequest
import sqls.{ distinct, count }


/**
 * User: bathily
 * Date: 01/10/13
 */
case class Permission(id: Long,
                      userId: Long,
                      user: Option[User] = None,
                      appId: Long,
                      app: Option[App] = None,
                      decision: Boolean,
                      scopes: Option[Seq[String]],
                      redirectUri: Option[String],
                      state: Option[String],
                      createdAt: DateTime,
                      revokedAt: Option[DateTime]) extends ShortenedNames with OauthPermission[App] {


  val client: App = app.orNull
  def authorized(request: AuthzRequest): Boolean = decision && revokedAt.isEmpty && request.redirectUri == redirectUri
}

object Permission extends SQLSyntaxSupport[Permission] with ShortenedNames {

  implicit val writes: Writes[Permission] =
    (
      (__ \ "id").write[Long] ~
      (__ \ "user").writeNullable[User] ~
      (__ \ "appId").write[Long] ~
      (__ \ "decision").write[Boolean] ~
      (__ \ "scopes").writeNullable[Seq[String]] ~
      (__ \ "redirectUri").writeNullable[String] ~
      (__ \ "state").writeNullable[String] ~
      (__ \ "createdAt").write[DateTime] ~
      (__ \ "revokedAt").writeNullable[DateTime]
    )(p => (p.id, p.user, p.appId, p.decision, p.scopes, p.redirectUri, p.state, p.createdAt, p.revokedAt))

  implicit val reads: Reads[Permission] =
    (
      (__ \ "id").read[Long] ~
      (__ \ "user").readNullable[User] ~
      (__ \ "appId").read[Long] ~
      (__ \ "decision").read[Boolean] ~
      (__ \ "scopes").readNullable[Seq[String]] ~
      (__ \ "redirectUri").readNullable[String] ~
      (__ \ "state").readNullable[String] ~
      (__ \ "createdAt").read[DateTime] ~
      (__ \ "revokedAt").readNullable[DateTime]
    )((id, user, appId, decision, scope, redirectUri, state, createdAt, revokedAt) =>
      Permission(id, user.map(_.id).getOrElse(0), user, appId, None, decision, scope, redirectUri, state, createdAt, revokedAt)
    )


  override val columnNames: Seq[String] = Seq("id", "user_id", "app_id", "decision", "scopes", "redirect_uri", "state", "created_at", "revoked_at")

  def apply(p: ResultName[Permission])(rs: WrappedResultSet): Permission = new Permission(
    id = rs.long(p.id),
    userId = rs.long(p.userId),
    appId = rs.long(p.appId),
    //TODO Check the doc for boolean mapping
    decision = rs.int(p.decision) == 1,
    scopes = rs.stringOpt(p.scopes).map(_.split(" ")),
    redirectUri = rs.stringOpt(p.redirectUri),
    state = rs.stringOpt(p.state),
    createdAt = rs.timestamp(p.createdAt).toDateTime,
    revokedAt = rs.timestampOpt(p.revokedAt).map(_.toDateTime)
  )

  def apply(p: SyntaxProvider[Permission])(rs: WrappedResultSet): Permission = apply(p.resultName)(rs)

  def apply(p: SyntaxProvider[Permission], a: SyntaxProvider[App])(rs: WrappedResultSet): Permission =
    apply(p.resultName)(rs).copy(app = Some(App(a)(rs)))

  def apply(p: SyntaxProvider[Permission], u: SyntaxProvider[User], a: SyntaxProvider[App])(rs: WrappedResultSet): Permission =
    apply(p.resultName)(rs).copy(user = Some(User(u)(rs)), app = Some(App(a)(rs)))

  lazy val p = Permission.syntax("p")
  private val u = User.u
  private val a = App.a

  private def query[A](userId: Long, appId:Long, unRevokedOnly: Boolean = true): PagingSQLBuilder[A] =
    select
      .from(Permission as p)
        .innerJoin(User as u).on(p.userId, u.id)
        .innerJoin(App as a).on(p.appId, a.pid)
      .where
        .eq(p.userId, userId)
        .and
        .eq(p.appId, appId)
        .and(sqls.toAndConditionOpt(
          if(unRevokedOnly) Some(sqls.isNull(p.revokedAt)) else None
        ))
      .orderBy(p.createdAt).desc

  def findAll(userId: Long, appId:Long, unRevokedOnly: Boolean)(implicit session: AsyncDBSession, cxt: EC): Future[List[Permission]] =
    withSQL {
      query(userId, appId, unRevokedOnly)
    }.map(Permission(p, u, a)).list().future()

  def findAll(user: User, app: App, unRevokedOnly: Boolean)(implicit session: AsyncDBSession, cxt: EC): Future[List[Permission]] =
    findAll(user.id, app.pid, unRevokedOnly)

  def find(userId: Long, appId:Long)(implicit session: AsyncDBSession, cxt: EC): Future[Option[Permission]] =
    withSQL {
      query(userId, appId).limit(1)
    }.map(Permission(p, u, a)).single().future()

  def find(user: User, app: App)(implicit session: AsyncDBSession, cxt: EC): Future[Option[Permission]] =
    find(user.id, app.pid)

  def authorizedCount(userId: Long)(implicit session: AsyncDBSession, cxt: EC): Future[Long] =
    withSQL {
      select(count(distinct(p.id)))
        .from(Permission as p)
        .where
        .eq(p.userId, userId)
        .and
        .isNull(p.revokedAt)
        .groupBy(p.id)
    }.map(_.long(0)).single().future().map(_.getOrElse(0))

  def authorized(userId: Long, page: Int)(implicit session: AsyncDBSession, cxt: EC): Future[List[Permission]] =
    withSQL {
      select
        .from(Permission as p)
        .innerJoin(App as a).on(p.appId, a.pid)
        .where
        .eq(p.userId, userId)
        .and
        .isNull(p.revokedAt)
        .groupBy(p.id)
        .orderBy(p.createdAt).desc
        .limit(30).offset(page)
    }.map(Permission(p, a)).list().future()


  def create(user: User, app: App, decision: Boolean, scopes: Option[Seq[String]], redirectUri: Option[String],
             state: Option[String], createdAt: DateTime = DateTime.now(), revokedAt: Option[DateTime] = None)
            (implicit session: AsyncDBSession, ec: EC): Future[Permission] = {
    withSQL {
      insert.into(Permission).namedValues(
        column.userId -> user.id,
        column.appId -> app.pid,
        column.decision -> decision,
        column.scopes -> scopes.map(_.mkString(" ")),
        column.redirectUri -> redirectUri,
        column.state -> state,
        column.createdAt -> createdAt,
        column.revokedAt -> revokedAt
      )
    }.updateAndReturnGeneratedKey().future.map(id =>
      Permission(
        id = id,
        userId = user.id,
        user = Some(user),
        appId = app.pid,
        app = Some(app),
        decision = decision,
        scopes = scopes,
        redirectUri = redirectUri,
        state = state,
        createdAt = createdAt,
        revokedAt = revokedAt
      )
    )
  }

}
