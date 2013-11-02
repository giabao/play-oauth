package controllers

import play.api.mvc.{Action, Controller}
import domain.DB._
import models.Permission
import domain.Security._

/**
 * User: bathily
 * Date: 01/10/13
 */
object Users extends Controller {

  def profile = TODO

  def apps = InTx { implicit tx =>
    WithUser(tx, dbContext) { implicit user =>
      Action.async { implicit request =>
        for {
          count <- Permission.authorizedCount(user.id)
          permissions <- Permission.authorized(user.id, 0) //if count > 0
        } yield {
          Ok(permissions.toString())
        }

      }
    }


  }
}
