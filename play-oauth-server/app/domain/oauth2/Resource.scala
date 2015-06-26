package domain.oauth2

import models.{App, AuthToken, User}
import play.api.Application
import play.api.mvc.EssentialAction
import scalikejdbc.async.AsyncDBSession
import scala.concurrent.ExecutionContext
import domain.DB._
import fr.njin.playoauth.rs.Oauth2Resource
import fr.njin.playoauth.Utils
import play.api.libs.json.Json
import play.api.http.Status

/**
 * Example of helpers to protect some resources
 * with Bearer authentication
 *
 * Usage for a local resource
 *
 * {{{
 *  def user = InTx { implicit tx =>
 *    Resource("basic") { user =>
 *      Action {
 *       Ok(Json.toJson(user))
 *      }
 *    }
 *  }
 * }}}
 *
 * Usage for a remote resource
 *
 * {{{
 *  def user = InTx { implicit tx =>
 *    Resource.remote("basic") { user =>
 *      Action {
 *       Ok(Json.toJson(user))
 *      }
 *    }
 *  }
 * }}}
 */
object Resource {

  def apply(scopes: String*)(action: User => EssentialAction)
           (implicit session: AsyncDBSession, ec: ExecutionContext = dbContext): EssentialAction = {
    Oauth2Resource.scoped[User](scopes:_*)(action)()(Oauth2Resource.localResourceOwner(new AuthTokenRepository())(Utils.parseBearer))
  }

  def remote(scopes: String*)(action: User => EssentialAction)
            (implicit app: Application, session: AsyncDBSession, ec: ExecutionContext = dbContext): EssentialAction = {
    Oauth2Resource.scoped[User](scopes:_*)(action)()(
      Oauth2Resource.basicAuthRemoteResourceOwner[AuthToken, User, App](
        "http://localhost:9000/oauth2/token","CLIENT_ID","CLIENT_SECRET"
      ){ response =>
        response.status match {
          case Status.OK => Json.fromJson[AuthToken](response.json).asOpt
          case _ => None
        }
      }(app, Utils.parseBearer, ec))
  }

}
