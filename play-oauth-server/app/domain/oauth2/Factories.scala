package domain.oauth2

import fr.njin.playoauth.common.domain._
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import models._
import scalikejdbc.async.AsyncDBSession

class AuthCodeFactory(implicit session:AsyncDBSession, ec: ExecutionContext) extends OauthCodeFactory[AuthCode, User, App] {

  def apply(owner: User, client: App, redirectUri: Option[String], scopes: Option[Seq[String]]): Future[AuthCode] =
    Permission.find(owner, client).flatMap(p =>
      AuthCode.create(UUID.randomUUID().toString, p.get, scopes, redirectUri)
    )

}

class AuthTokenFactory(implicit session:AsyncDBSession, ec: ExecutionContext) extends OauthTokenFactory[AuthToken, User, App] {

  def apply(owner: User, client: App, redirectUri: Option[String], scopes: Option[Seq[String]]): Future[AuthToken] =
    Permission.find(owner, client).flatMap(p =>
      AuthToken.create(p.get, UUID.randomUUID().toString, "Bearer", UUID.randomUUID().toString)
    )

}