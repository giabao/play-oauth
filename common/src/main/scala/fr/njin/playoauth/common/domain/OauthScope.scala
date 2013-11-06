package fr.njin.playoauth.common.domain

import scala.concurrent.Future

/**
 * Represents an oauth2 scope
 */
trait OauthScope {
  /**
   * @return the scope's id. Must be unique
   */
  def id: String

  /**
   * @return the name of the scope
   */
  def name: Option[String]

  /**
   * @return the description of the scope
   */
  def description: Option[String]
}

/**
 * Repository used to find a scope by its id
 * @tparam T Type of the scope
 */
trait OauthScopeRepository[T <: OauthScope] {
  def find(id:String*):Future[Map[String, T]]
}

class BasicOauthScope(val id: String,
                      val name: Option[String] = None,
                      val description: Option[String] = None) extends OauthScope

