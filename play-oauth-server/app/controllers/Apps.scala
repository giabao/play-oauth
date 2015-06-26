package controllers

import play.api.mvc.{Action, Result, EssentialAction, Controller}
import domain.DB._
import domain.Security._
import scalikejdbc.async.AsyncDBSession
import models.{User, App}
import play.api.data.Form
import play.api.data.Forms._
import fr.njin.playoauth.as.endpoints.Constraints._
import fr.njin.playoauth.as.endpoints.Requests._
import scala.concurrent.Future
import play.api.i18n.Messages
import play.api.libs.iteratee.{Iteratee, Done}

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

  val OnAppNotFound: Long => User => EssentialAction = id => implicit user => EssentialAction { implicit request =>
    Done[Array[Byte], Result](NotFound(views.html.apps.notfound(id)))
  }

  val OnAppForbidden: Long => User => EssentialAction = id => implicit user => EssentialAction { implicit request =>
    Done[Array[Byte], Result](Forbidden(views.html.apps.notfound(id)))
  }

  def CanAccessApp(id:Long, user:User,
                   onNotFound: Long => User => EssentialAction = OnAppNotFound,
                   onForbidden: Long => User =>EssentialAction = OnAppForbidden
                  )(action: App => AsyncDBSession => EssentialAction)(implicit session: AsyncDBSession): EssentialAction =
    EssentialAction { request =>
      Iteratee.flatten(
        App.find(id).map(_.fold(onNotFound(id)(user)(request))(app => {
          if(app.ownerId == user.id)
            action(app)(session)(request)
          else
            onForbidden(id)(user)(request)
        }))
      )
    }

  def WithApp(id: Long)(action: User => App => AsyncDBSession => EssentialAction): EssentialAction =
    InTx { implicit tx =>
      WithUser(tx, dbContext) { user =>
        CanAccessApp(id, user)(action(user))
      }
    }

  def list = InTx { implicit tx =>
    AuthenticatedAction.async { implicit request =>
      App.findForOwner(request.user).map { apps =>
        Ok(views.html.apps.list(apps))
      }
    }
  }

  def create = InTx { implicit tx =>
    AuthenticatedAction.apply { implicit request =>
      Ok(views.html.apps.create(appForm))
    }
  }


  def doCreate = InTx { implicit tx =>
    AuthenticatedAction.async { implicit request =>
      appForm.bindFromRequest.fold(f => Future.successful(BadRequest(views.html.apps.create(f))),
        app => App.create(request.user,
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
      )
    }
  }


  def app(id: Long) = WithApp(id) { implicit user => app => implicit tx =>
    Action { implicit request =>
      Ok(views.html.apps.app(app))
    }
  }

  def edit(id: Long) = WithApp(id) { implicit user => app => implicit tx =>
    Action { implicit request =>
      Ok(views.html.apps.edit(app, appForm.fill(AppForm(app))))
    }
  }

  def doEdit(id: Long) = WithApp(id) { implicit user => app => implicit tx =>
    Action.async { implicit request =>
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
    }
  }

  def delete(id: Long) = WithApp(id) { implicit user => app => implicit tx =>
    Action { implicit request =>
      Ok(views.html.apps.delete(app))
    }
  }

  def doDelete(id: Long) = WithApp(id) { implicit user => app => implicit tx =>
    Action.async { implicit request =>
      app.destroy().map(app =>
        Redirect(routes.Apps.list).flashing("success" -> Messages("flash.app.delete.success", app.name))
      )
    }
  }

}
