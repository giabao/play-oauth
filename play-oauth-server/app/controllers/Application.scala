package controllers

import javax.inject.{Inject, Singleton}

import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import scala.concurrent.Future
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import scalikejdbc.async.AsyncDB
import models.User
import domain.DB._
import domain.Security._

@Singleton class Application @Inject() (val messagesApi: MessagesApi) extends Controller with I18nSupport {

  def index = InTx { implicit tx =>
    AuthenticatedAction.apply { implicit request =>
      Ok(views.html.index())
    }
  }

  val signUpForm = Form(
    tuple(
      "firstName" -> nonEmptyText,
      "lastName" -> nonEmptyText,
      "email" -> email.verifying(nonEmpty),
      "password" -> nonEmptyText
    )
  )

  val signInForm = Form(
    tuple(
      "email" -> email.verifying(nonEmpty),
      "password" -> nonEmptyText
    )
  )

  def signIn(back: Option[String]) = Action { implicit request =>
    Ok(views.html.signin(back, signInForm))
  }

  def doSignIn(back: Option[String]) = Action.async { implicit request =>
    val form = signInForm.bindFromRequest

    form.fold(f => Future.successful(BadRequest(views.html.signin(back, f))), {
      case (email, password) => {
        AsyncDB.withPool {
          implicit tx =>
            User.findByEmail(email).flatMap(_.fold(Future.successful(BadRequest(views.html.signin(back, form.withGlobalError("error.user.not.found"))))) {
              u =>
                u.passwordMatch(password) match {
                  case true => AuthenticatedAction.logIn(u, back.getOrElse(routes.Application.index.url))
                  case false => Future.successful(BadRequest(views.html.signin(back, form.withGlobalError("error.credentials.not.match"))))
                }
            })
        }
      }
    })

  }

  def signUp(back: Option[String]) = Action { implicit request =>
    Ok(views.html.signup(back, signUpForm))
  }

  def doSignUp(back: Option[String]) = Action.async { implicit request =>

    val form = signUpForm.bindFromRequest

    form.fold(f => Future.successful(BadRequest(views.html.signup(back, f))), {
      case (firstName, lastName, email, password) => {
        AsyncDB.withPool {
          implicit tx =>
            User.findByEmail(email).flatMap(_.fold(
              User.create(email, password, firstName, lastName).flatMap(u =>
                AuthenticatedAction.logIn(u, back.getOrElse(routes.Application.index.url))
              )) {
              u =>
                Future.successful(BadRequest(views.html.signup(back, form.withGlobalError("error.user.exist"))))
            }
            )
        }
      }
    })

  }

  def logout = InTx { implicit tx =>
    AuthenticatedAction.async { implicit request =>
      AuthenticatedAction.logOut(request.user)
    }
  }
}