package fr.njin.playoauth.common.domain

import scala.concurrent.Future

/**
 * Represents the oauth2 resource owner
 */
trait OauthResourceOwner {
  def id: String
}

trait OauthResourceOwnerRepository[T <: OauthResourceOwner] {
  def find(id:String):Future[Option[T]]
}