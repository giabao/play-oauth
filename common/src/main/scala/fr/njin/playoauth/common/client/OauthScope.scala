package fr.njin.playoauth.common.client

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

class BasicOauthScope(val id: String, val name: Option[String], val description: Option[String]) extends OauthScope

trait OauthScopeRepository[T <: OauthScope] {
  def find(id:String)(implicit ec:ExecutionContext):Future[Option[T]]
  def save(client:T)(implicit ec:ExecutionContext):Future[T]
  def delete(client:T)(implicit ec:ExecutionContext):Future[Unit]
}