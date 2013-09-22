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
  def defaults(implicit ec:ExecutionContext):Future[Option[Seq[T]]]

  def find(id:String)(implicit ec:ExecutionContext):Future[Option[T]]
  def find(id:String*)(implicit ec:ExecutionContext):Future[Seq[(String,Option[T])]]

  def save(scope:T)(implicit ec:ExecutionContext):Future[T]
  def delete(scope:T)(implicit ec:ExecutionContext):Future[Unit]
}