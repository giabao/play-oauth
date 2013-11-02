package controllers

import play.api.mvc.{Action, Controller}

import models.User._
import play.api.libs.json.Json
import domain.DB._
import domain.oauth2.Resource

object API extends Controller {

  def user = InTx { implicit tx =>
    Resource("basic") { user =>
      Action {
        Ok(Json.toJson(user))
      }
    }
  }

}

