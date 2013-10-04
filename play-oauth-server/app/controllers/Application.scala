package controllers

import play.api.mvc._
import scala.concurrent.{Future, ExecutionContext}
import ExecutionContext.Implicits.global
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import scalikejdbc.async.AsyncDB
import models.User
import play.api.libs.Crypto
import domain.Authenticated

object Application extends Controller {

  def index = Authenticated { implicit request =>
    Ok(views.html.index())
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

    form.fold(f => Future.successful(BadRequest(views.html.signin(back, f))), _ match {
      case (email, password) => {
        AsyncDB.localTx { implicit tx =>
          User.findByEmail(email).flatMap(_.fold(Future.successful(BadRequest(views.html.signin(back, form.withGlobalError("error.user.not.found"))))){ u =>
            u.password == Crypto.encryptAES(password) match {
              case true => domain.Authenticated.logIn(u, back.getOrElse(routes.Application.index.url))
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

    form.fold(f => Future.successful(BadRequest(views.html.signup(back, f))), _ match {
      case (firstName, lastName, email, password) => {
        AsyncDB.localTx { implicit tx =>
          User.findByEmail(email).flatMap(_.fold(
            User.create(email, Crypto.encryptAES(password), firstName, lastName).flatMap(u =>
              domain.Authenticated.logIn(u, back.getOrElse(routes.Application.index.url))
            )){ u =>
              Future.successful(BadRequest(views.html.signup(back, form.withGlobalError("error.user.exist"))))
            }
          )
        }
      }
    })

  }

  def logout = Authenticated.async { implicit request =>
    Authenticated.logOut(request.user)
  }
}