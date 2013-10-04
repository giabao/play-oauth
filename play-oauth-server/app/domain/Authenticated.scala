package domain

import models.User
import scalikejdbc.async.AsyncDB
import play.api.libs.Crypto
import scala.concurrent.Future
import play.api.mvc.{SimpleResult, Results}
import controllers.routes
import domain.Security.AuthenticatedActionBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import java.util.UUID
import org.mindrot.jbcrypt.BCrypt


trait AuthenticationHandler {

  def logIn(user:User, redirectUrl: String): Future[SimpleResult] = {
    Future.successful(Results.Redirect(redirectUrl).withNewSession
      .withSession(
      "sessionId" -> UUID.randomUUID().toString,
      "email" -> Crypto.encryptAES(user.email),
      "password" -> BCrypt.hashpw(user.password, BCrypt.gensalt()))
    )
  }

  def logOut(user:User): Future[SimpleResult] = {
    Future.successful(Results.Redirect(routes.Application.signIn()).withNewSession)
  }
}

object Authenticated extends AuthenticatedActionBuilder[User](
  request => {
    (for {
      sessionId <- request.session.get("sessionId")
      email <- request.session.get("email")
      password <- request.session.get("password")
    } yield (sessionId, email, password))
      .map(credentials => AsyncDB.localTx(implicit tx =>
        User.findByEmail(Crypto.decryptAES(credentials._2)).map(_.filter(u => BCrypt.checkpw(u.password, credentials._3)))
      )).getOrElse(Future.successful(None))
  },
  request => Future.successful(Results.Redirect(routes.Application.signIn(Option(request.uri)).url))
) with AuthenticationHandler