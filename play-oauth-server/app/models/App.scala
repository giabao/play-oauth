package models

import fr.njin.playoauth.common.domain.OauthClient
import org.joda.time.DateTime
import fr.njin.playoauth.common.OAuth
import scalikejdbc._, async._, SQLInterpolation._
import scala.concurrent.Future
import java.util.UUID

/**
 * User: bathily
 * Date: 01/10/13
 */
case class App(pid: Long,
               ownerId: Long,
               owner: Option[User] = None,
               id: String,
               secret: String,
               name: String,
               description: String,
               uri: String,
               iconUri: Option[String],
               redirectUris: Option[Seq[String]],
               isWebApp: Boolean,
               isNativeApp: Boolean,
               createdAt: DateTime) extends ShortenedNames {

  def save()(implicit session: AsyncDBSession, cxt: EC): Future[App] = App.save(this)

  /*
  val redirectUri: Option[String] = redirectUris.flatMap(_.headOption)
  val clientUri: Option[String] = uri

  val authorized: Boolean = true

  val allowedResponseType: Seq[String] = (isWebApp, isNativeApp) match {
    case (true, true) => OAuth.ResponseType.All
    case (true, false) => Seq(OAuth.ResponseType.Code)
    case (false, true) => Seq(OAuth.ResponseType.Token)
    case _  => Seq.empty
  }

  val allowedGrantType: Seq[String] = Seq(OAuth.GrantType.AuthorizationCode, OAuth.GrantType.ClientCredentials, OAuth.GrantType.RefreshToken)

  val issuedAt: Long = createdAt.getMillis
  */
}

object App extends SQLSyntaxSupport[App] with ShortenedNames {

  override val columnNames: Seq[String] = Seq("pid", "owner_id", "id", "secret", "name", "description", "uri", "icon_uri",
    "redirect_uris", "is_web_app", "is_native_app", "created_at")

  def apply(a: SyntaxProvider[App])(rs: WrappedResultSet): App = apply(a.resultName)(rs)

  def apply(a: ResultName[App])(rs: WrappedResultSet): App = new App(
    pid = rs.long(a.pid),
    ownerId = rs.long(a.ownerId),
    id = rs.string(a.id),
    secret = rs.string(a.secret),
    name = rs.string(a.name),
    description = rs.string(a.description),
    uri = rs.string(a.uri),
    iconUri = rs.stringOpt(a.iconUri),
    redirectUris = rs.stringOpt(a.redirectUris).map(_.split(",")),
    isWebApp = rs.boolean(a.isWebApp),
    isNativeApp = rs.boolean(a.isNativeApp),
    createdAt = rs.timestamp(a.createdAt).toDateTime
  )

  def apply(a: SyntaxProvider[App], u: SyntaxProvider[User])(rs: WrappedResultSet): App =
    apply(a)(rs).copy(owner = Some(User(u)(rs)))


  lazy val a = App.syntax("a")

  private val u = User.u

  def find(id: Long)(implicit session: AsyncDBSession, cxt: EC): Future[Option[App]] = {
    withSQL {
      select
        .from[App](App as a)
        .leftJoin(User as u).on(a.ownerId, u.id)
        .where.eq(a.pid, id)
    }.map(App(a, u)).single.future
  }

  def findForOwner(owner: User)(implicit session: AsyncDBSession, cxt: EC): Future[List[App]] =
    findForOwner(owner.id)

  def findForOwner(ownerId: Long)(implicit session: AsyncDBSession, cxt: EC): Future[List[App]] = {
    withSQL {
      select
        .from[App](App as a)
        .leftJoin(User as u).on(a.ownerId, u.id)
        .where.eq(a.ownerId, ownerId)
        .orderBy(a.id)
    }.map(App(a, u)).list.future
  }

  def create(owner: User, id: String = UUID.randomUUID().toString, secret: String = UUID.randomUUID().toString,
             name: String, description: String, uri: String, iconUri: Option[String],
             redirectUris: Option[Seq[String]], isWebApp: Boolean, isNativeApp: Boolean, createdAt: DateTime = DateTime.now())
            (implicit session: AsyncDBSession, ctx: EC): Future[App] = {

    withSQL {
      insert.into(App).namedValues(
        column.ownerId -> owner.id,
        column.id -> id,
        column.secret -> secret,
        column.name -> name,
        column.description -> description,
        column.uri -> uri,
        column.iconUri -> iconUri,
        column.redirectUris -> redirectUris.map(_.mkString(",")),
        column.isWebApp -> isWebApp,
        column.isNativeApp -> isNativeApp,
        column.createdAt -> createdAt
      )
    }.updateAndReturnGeneratedKey().future.map(App(_, owner.id, Option(owner), id, secret, name, description, uri, iconUri, redirectUris, isWebApp, isNativeApp, createdAt))
  }

  def save(app: App)(implicit session: AsyncDBSession, cxt: EC): Future[App] = {
    withSQL {
      update(App).set(
        column.name -> app.name,
        column.description -> app.description,
        column.uri -> app.uri,
        column.iconUri -> app.iconUri,
        column.redirectUris -> app.redirectUris.map(_.mkString(",")),
        column.isWebApp -> app.isWebApp,
        column.isNativeApp -> app.isNativeApp
      ).where.eq(column.pid, app.pid)
    }.update.future.map(_ => app)
  }
}