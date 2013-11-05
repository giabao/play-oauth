package controllers

import play.api.mvc.{Action, Controller}
import models._
import fr.njin.playoauth.as.endpoints.AuthorizationEndpoint
import fr.njin.playoauth.as.endpoints.Constraints._
import fr.njin.playoauth.as.endpoints.Requests._
import fr.njin.playoauth.common.domain.{OauthResourceOwnerPermission, BasicOauthScope}
import scalikejdbc.async.AsyncDBSession
import domain.DB._
import domain.Security._
import scala.concurrent.{Future, ExecutionContext}
import play.api.data.Form
import play.api.data.Forms._
import fr.njin.playoauth.common.OAuth
import fr.njin.playoauth.common.request.AuthzRequest
import domain.oauth2._
import scala.Some

/**
 * User: bathily
 * Date: 03/10/13
 */
object Authorization extends Controller {

  case class PermissionForm(appId: Long,
                            decision: Boolean,
                            scope: Option[Seq[String]],
                            redirectUri: Option[String],
                            state: Option[String])

  val permissionForm =  Form (
    mapping(
      "appId" -> longNumber,
      "decision" -> boolean,
      "scope" -> optional(of[Seq[String]](scopeFormatter)),
      "redirectUri" -> optional(text.verifying(uri)),
      "state" -> optional(text)
    )(PermissionForm.apply)(PermissionForm.unapply)
  )

  def authz(permission:Option[Long]) = InTx { implicit tx =>
    WithUser(tx, dbContext) { implicit user =>
      Action.async(parse.empty) { request =>
        new AuthorizationEndpointController(permission).authorize( _ => Some(user))(
          (ar, c) => implicit r => Future.failed(new Exception()),
          (ar, c) => implicit r => {
            Future.successful(Ok(views.html.authorize(c, permissionForm.fill(PermissionForm(c.pid, decision = false, ar.scope, ar.redirectUri, ar.state)))))
          }
        ).apply(request)
      }
    }
  }

  // FIXME Use a CRSF filter otherwise the client can steal a permission by making a direct post
  def authorize = InTx { implicit tx =>
    WithUser(tx, dbContext) { implicit user =>
      Action.async { implicit request =>
        permissionForm.bindFromRequest.fold(f => Future.successful(BadRequest("")), permission => {
          App.find(permission.appId).flatMap(_.fold(Future.successful(NotFound(""))) { app =>
            Permission.create(user, app, permission.decision,
              permission.scope, permission.redirectUri, permission.state
            ).map { p =>
              Redirect(routes.Authorization.authz(Some(p.id)).url,
                AuthzRequest(OAuth.ResponseType.Code,
                  app.id,
                  permission.redirectUri,
                  permission.scope,
                  permission.state
                )
              )
            }
          })
        })
      }
    }
  }
}

class OwnerPermissions(lastPermission: Option[Long])(implicit session:AsyncDBSession, ec: ExecutionContext)
  extends OauthResourceOwnerPermission[User, App, Permission]{

  def apply(user: User, client: App): Future[Option[Permission]] = Permission.find(user, client).map(_.flatMap { p =>
    if(!p.decision) lastPermission.flatMap(id => if(id != p.id) None else Some(p))
    else Some(p)
  })
}

class AuthorizationEndpointController(lastPermission: Option[Long])(implicit val session:AsyncDBSession, ec: ExecutionContext)
  extends AuthorizationEndpoint[App, BasicOauthScope, AuthCode, User, Permission, AuthToken] (
  new OwnerPermissions(lastPermission),
  new AppRepository(),
  new InMemoryOauthScopeRepository[BasicOauthScope](Seq(new BasicOauthScope("basic")).map(s => s.id -> s).toMap),
  new AuthCodeFactory(),
  new AuthTokenFactory()
)