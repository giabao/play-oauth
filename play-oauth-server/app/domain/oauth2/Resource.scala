package domain.oauth2

import models.{App, Permission, AuthToken, User}
import play.api.mvc.EssentialAction
import scalikejdbc.async.AsyncDBSession
import scala.concurrent.ExecutionContext
import domain.DB._
import fr.njin.playoauth.rs.Oauth2Resource
import fr.njin.playoauth.Utils
import play.api.libs.json.Json
import play.api.http.Status

object Resource {

  def apply(scopes: String*)(action: User => EssentialAction)(implicit session: AsyncDBSession, ec: ExecutionContext = dbContext): EssentialAction = {
    Oauth2Resource.scoped[User](scopes:_*)(action)()(Oauth2Resource.localResourceOwner(new AuthTokenRepository())(Utils.parseBearer))
  }

  def remote(scopes: String*)(action: User => EssentialAction)(implicit session: AsyncDBSession, ec: ExecutionContext = dbContext): EssentialAction = {
    Oauth2Resource.scoped[User](scopes:_*)(action)()(Oauth2Resource.remoteResourceOwner[AuthToken, User, Permission, App]
      ("http://localhost:9000/oauth2/token","CLIENT_ID","CLIENT_SECRET"){ response => response.status match {
      case Status.OK => Json.fromJson[AuthToken](response.json).asOpt
      case _ => None
    }}(Utils.parseBearer))
  }

}
