package fr.njin.playoauth.common.domain

import scala.concurrent.{Future, ExecutionContext}

/**
 * User: bathily
 * Date: 19/09/13
 */
trait OauthScope {
  def id: String
  def name: Option[String]
  def description: Option[String]
}

class BasicOauthScope(val id: String,
                      val name: Option[String] = None,
                      val description: Option[String] = None) extends OauthScope

trait OauthScopeRepository[T <: OauthScope] {
  def defaults:Future[Option[Seq[T]]]

  def find(id:String):Future[Option[T]]
  def find(id:String*):Future[Seq[(String,Option[T])]]

}