package models

import org.joda.time.DateTime
import scalikejdbc._, async._, SQLInterpolation._
import scala.concurrent.Future

/**
 * User: bathily
 * Date: 01/10/13
 */
case class Permission(id: Long,
                      userId: Long,
                      user: Option[User] = None,
                      appId: Long,
                      app: Option[App] = None,
                      scope: Option[Seq[String]],
                      redirectUri: Option[String],
                      createdAt: DateTime,
                      revokedAt: Option[DateTime]) extends ShortenedNames {

  /*
  val client: App = app
  def authorized(request: AuthzRequest): Boolean = revokedAt.isEmpty && request.redirectUri == redirectUri
  */
}

object Permission extends SQLSyntaxSupport[Permission] with ShortenedNames {

  override val columnNames: Seq[String] = Seq("id", "user_id", "app_id", "scope", "redirect_uri", "created_at", "revoked_at")

  def create(userId: Long, appId: Long, scope: Option[Seq[String]], redirectUri: Option[String],
             createdAt: DateTime = DateTime.now(), revokedAt: Option[DateTime] = None)
            (implicit session: AsyncDBSession, ec: EC): Future[Permission] = {
    withSQL {
      insert.into(Permission).namedValues(
        column.userId -> userId,
        column.appId -> appId,
        column.scope -> scope.map(_.mkString(" ")),
        column.redirectUri -> redirectUri,
        column.createdAt -> createdAt,
        column.revokedAt -> revokedAt
      )
    }.updateAndReturnGeneratedKey().future.map(id =>
      Permission(
        id = id,
        userId = userId,
        appId = appId,
        scope = scope,
        redirectUri = redirectUri,
        createdAt = createdAt,
        revokedAt = revokedAt
      )
    )
  }
}
