package controllers

import fr.njin.playoauth.as.endpoints.{AuthzEndpointController, AuthzEndpoint}
import play.api._
import play.api.mvc._
import scala.concurrent.ExecutionContext

object Application extends Controller {

  import ExecutionContext.Implicits.global

  def index = AuthzEndpointController.authorize
  
}