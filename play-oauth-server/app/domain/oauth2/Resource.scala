package domain.oauth2

import models.User
import play.api.mvc.EssentialAction
import scalikejdbc.async.AsyncDBSession
import scala.concurrent.ExecutionContext
import domain.DB._
import fr.njin.playoauth.rs.Oauth2Resource
import fr.njin.playoauth.Utils

object Resource {

  def apply(scopes: String*)(action: User => EssentialAction)(implicit session: AsyncDBSession, ec: ExecutionContext = dbContext): EssentialAction = {
    Oauth2Resource.scoped[User](scopes:_*)(action)()(Oauth2Resource.localResourceOwner(new AuthTokenRepository())(Utils.parseBearer))
  }

}
