package controllers

import play.api.mvc.Controller
import domain.Authenticated
import scalikejdbc.async.AsyncDB
import models.App
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.data.Form
import play.api.data.Forms._
import fr.njin.playoauth.as.endpoints.Constraints._
import scala.Some
import fr.njin.playoauth.as.endpoints.Requests._
import scala.concurrent.Future

/**
 * User: bathily
 * Date: 03/10/13
 */
object Apps extends Controller {


  case class AppForm(name: String,
                     description: String,
                     uri: String,
                     iconUri: Option[String],
                     redirectUris: Option[Seq[String]],
                     isWebApp: Boolean,
                     isNativeApp: Boolean)

  object AppForm {

    def apply(app: App): AppForm = AppForm(
      app.name,
      app.description,
      app.uri,
      app.iconUri,
      app.redirectUris,
      app.isWebApp,
      app.isNativeApp
    )

  }

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

  def create = Authenticated { implicit request =>
    Ok(views.html.apps.create(appForm))
  }

  def doCreate = Authenticated.async { implicit request =>
    appForm.bindFromRequest.fold(f => Future.successful(BadRequest(views.html.apps.create(f))),
      app => AsyncDB.localTx { implicit tx =>
        App.create(request.user,
          name = app.name,
          description = app.description,
          uri = app.uri,
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

  def app(id: Long) = Authenticated.async { implicit request =>
    AsyncDB.localTx { implicit tx =>
      App.find(id).map(_.fold(NotFound(views.html.apps.notfound(id)))(app => {
        Ok(views.html.apps.app(app))
      }))
    }
  }

  def edit(id: Long) = Authenticated.async { implicit request =>
    AsyncDB.localTx { implicit tx =>
      App.find(id).map(_.fold(NotFound(views.html.apps.notfound(id)))(app => {
        Ok(views.html.apps.edit(app, appForm.fill(AppForm(app))))
      }))
    }

  }

  def doEdit(id: Long) = Authenticated.async { implicit request =>
    AsyncDB.localTx { implicit tx =>
      App.find(id).flatMap(_.fold(Future.successful(NotFound(views.html.apps.notfound(id))))(app => {
        appForm.bindFromRequest.fold(f => Future.successful(BadRequest(views.html.apps.edit(app, f))),
          form =>
            app.copy(
              name = form.name,
              description = form.description,
              uri = form.uri,
              iconUri = form.iconUri,
              redirectUris = form.redirectUris,
              isWebApp = form.isWebApp,
              isNativeApp = form.isNativeApp
            ).save.map { a =>
              Redirect(routes.Apps.app(a.pid))
            }
        )
      }))
    }
  }

  def delete(id: Long) = TODO

  def doDelete(id: Long) = TODO

}
