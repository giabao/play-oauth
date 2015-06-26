package controllers

import javax.inject.{Inject, Singleton}

import play.api.i18n.{I18nSupport, MessagesApi}
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

object Authorization {
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
}

/**
 * User: bathily
 * Date: 03/10/13
 */
@Singleton class Authorization @Inject() (implicit val messagesApi: MessagesApi) extends Controller with I18nSupport {
  import Authorization._

  /**
   * Authorization endpoint call
   *
   * @param permission id of the newly created permission.
   *                   Provided by [[authorize]]'s redirection
   * @return
   */
  def authz(permission:Option[Long]) = InTx { implicit tx =>
    WithUser(tx, dbContext) { implicit user =>
      Action.async(parse.empty) { request =>
        new AuthorizationEndpointController(permission).authorize( _ => Some(user))(
          //this can't happen because this action is executed with an user in the context
          (ar, c) => implicit r => Future.failed(new Exception()),
          //If unauthorized, we show a permission form to the user
          (ar, c) => implicit r => {
            Future.successful(Ok(views.html.authorize(c, permissionForm.fill(PermissionForm(c.pid, decision = false, ar.scopes, ar.redirectUri, ar.state)))))
          },
          e => Future.successful(NotFound(e)),
          e => Future.successful(BadRequest(e))
        ).apply(request)
      }
    }
  }

  /**
   * Create the permission then redirect to [[authz]]
   * to finish the authorization process.
   * @return
   */
  // FIXME The client can steal a permission by making a direct post
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

/**
 * Our permission provider for the authorization endpoint
 *
 * This provider search the last created and non revoked permission for
 * the user and the client. If the found permission is not granted and
 * is not the lastPermission, the provider filters the permission
 * in order to allow the endpoint to ask another one to the user
 *
 * @param lastPermission
 * @param session the database session
 * @param ec the database execution context
 */
class OwnerPermissions(lastPermission: Option[Long])(implicit session:AsyncDBSession, ec: ExecutionContext)
  extends OauthResourceOwnerPermission[User, App, Permission]{

  def apply(user: User, client: App): Future[Option[Permission]] = Permission.find(user, client).map(_.flatMap { p =>
    //If not granted and is not lastPermission, return None
    if(!p.decision) lastPermission.flatMap(id => if(id != p.id) None else Some(p))
    else Some(p)
  })
}

/**
 * We need a custom authorization endpoint because
 * we need to pass the database session and the
 * database execution context to all our
 * repositories and factories
 *
 * @param lastPermission
 * @param session the database session
 * @param ec the database execution context
 */
class AuthorizationEndpointController(lastPermission: Option[Long])
                                     (implicit session:AsyncDBSession, ec: ExecutionContext, val messagesApi: MessagesApi)
  extends AuthorizationEndpoint[App, BasicOauthScope, AuthCode, User, Permission, AuthToken] (
  new OwnerPermissions(lastPermission),
  new AppRepository(),
  new InMemoryOauthScopeRepository[BasicOauthScope](Seq(new BasicOauthScope("basic")).map(s => s.id -> s).toMap), //TODO Create a "ConfOauthScopeRepository"
  new AuthCodeFactory(),
  new AuthTokenFactory()
)
