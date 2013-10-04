package controllers

import play.api.mvc.Controller
import domain.Authenticated
import scalikejdbc.async.AsyncDB
import models.{User, App}
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.data.Form
import play.api.data.Forms._
import fr.njin.playoauth.as.endpoints.Constraints._

/**
 * User: bathily
 * Date: 03/10/13
 */
object Apps extends Controller {

  case class AppForm(name: Option[String],
                     description: Option[String],
                     uri: Option[String],
                     iconUri: Option[String],
                     redirectUris: Option[Seq[String]],
                     isWebApp: Boolean,
                     isNativeApp: Boolean)

  val appForm = Form(
    mapping (
      "name" -> optional(text),
      "description" -> optional(text),
      "uri" -> optional(text),
      "iconUri" -> optional(text.verifying(uri)),
      "redirectUris" -> optional(seq(text.verifying(uri))),
      "isWebApp" -> boolean,
      "isNativeApp" -> boolean
    )(AppForm.apply)(AppForm.unapply)
  )

  def list = Authenticated.async { implicit request =>
    AsyncDB.localTx { implicit tx =>
      App.findForOwner(request.user).map { apps =>
        Ok(views.html.apps.list(apps))
      }
    }
  }

  def create = Authenticated { implicit request =>
    Ok(views.html.apps.create(appForm))
  }

  def doCreate = TODO

  def app(id: Long) = TODO

  def edit(id: Long) = TODO

  def doEdit(id: Long) = TODO

  def delete(id: Long) = TODO

  def doDelete(id: Long) = TODO

}
