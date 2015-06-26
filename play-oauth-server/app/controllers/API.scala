package controllers

import javax.inject.Singleton

import play.api.mvc.{Action, Controller}

import models.User._
import play.api.libs.json.Json
import domain.DB._
import domain.oauth2.Resource

@Singleton class API extends Controller {
  /**
   * A example of resource protection
   *
   * @return
   */
  def user = InTx { implicit tx =>
    Resource("basic") { user =>
      Action {
        Ok(Json.toJson(user))
      }
    }
  }

}

