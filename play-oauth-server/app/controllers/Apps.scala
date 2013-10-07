package controllers

import play.api.mvc.Controller
import domain.Authenticated
import scalikejdbc.async.AsyncDB
import models.App
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.data.Form
import play.api.data.Forms._
import fr.njin.playoauth.as.endpoints.Constraints._
import scala.concurrent.Future
import play.api.data.format.Formatter
import play.api.data.validation.{Valid, Invalid, Constraint}
import org.apache.commons.validator.routines.UrlValidator
import play.api.data.FormError
import scala.Some

/**
 * User: bathily
 * Date: 03/10/13
 */
object Apps extends Controller {

  val urisFormatter: Formatter[Seq[String]] = new Formatter[Seq[String]] {
    def unbind(key: String, value: Seq[String]): Map[String, String] = Map((key, value.mkString(",")))
    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Seq[String]] =
      data.get(key).fold[Either[Seq[FormError], Seq[String]]](Left(Seq(FormError(key, "error.required", Nil)))){ v =>
        Right(v.split(",").toSeq.map(_.trim))
      }
  }

  case class AppForm(name: String,
                     description: String,
                     uri: String,
                     iconUri: Option[String],
                     redirectUris: Option[Seq[String]],
                     isWebApp: Boolean,
                     isNativeApp: Boolean)

  val appForm = Form(
    mapping (
      "name" -> nonEmptyText,
      "description" -> nonEmptyText,
      "uri" -> nonEmptyText.verifying(uri),
      "iconUri" -> optional(text.verifying(uri)),
      "redirectUris" -> optional(of[Seq[String]](urisFormatter).verifying(uris)),
      "isWebApp" -> boolean,
      "isNativeApp" -> boolean
    )(AppForm.apply)(AppForm.unapply).verifying("error.redirectUri.required", app => {
      !(app.isWebApp || app.isNativeApp) || app.redirectUris.exists(!_.isEmpty)
    })
  )

  def list = Authenticated.async { implicit request =>
    AsyncDB.localTx { implicit tx =>
      App.findForOwner(request.user).map { apps =>
        Ok(views.html.apps.list(apps))
      }
    }
  }

  def create = Authenticated.async { implicit request =>
    AsyncDB.localTx { implicit tx =>
      App.findForOwner(request.user).map { apps =>
        Ok(views.html.apps.create(appForm, apps))
      }
    }
  }

  def doCreate = Authenticated.async { implicit request =>
    AsyncDB.localTx { implicit tx =>
      appForm.bindFromRequest.fold(f =>
        App.findForOwner(request.user).map { apps =>
          BadRequest(views.html.apps.create(f, apps))
        }, app => {
          App.create(request.user,
            name = app.name,
            description = Some(app.description),
            uri = Some(app.uri),
            iconUri = app.iconUri,
            redirectUris = app.redirectUris,
            isWebApp = app.isWebApp,
            isNativeApp = app.isNativeApp
          ).map { a =>
            Redirect(routes.Apps.app(a.pid))
          }
        }
      )
    }
  }

  def app(id: Long) = TODO

  def edit(id: Long) = TODO

  def doEdit(id: Long) = TODO

  def delete(id: Long) = TODO

  def doDelete(id: Long) = TODO

}
