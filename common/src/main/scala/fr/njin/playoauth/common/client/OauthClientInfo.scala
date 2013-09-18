package fr.njin.playoauth.common.client

import scala.concurrent.{ExecutionContext, Future}
import java.util.Date

/**
 * User: bathily
 * Date: 17/09/13
 */

trait OauthClientInfo {
  def redirectUri:Option[String]
  def clientUri:Option[String]
  def description: Option[String]
  def name: Option[String]
  def iconUri: Option[String]
}

trait OauthClient extends OauthClientInfo {
  def id:String
  def secret:String
  def issuedAt:Long
}

trait OauthClientRepository[T <: OauthClient] {
  def find(id:String)(implicit ec:ExecutionContext):Future[Option[T]]
  def save(client:T)(implicit ec:ExecutionContext):Future[T]
  def delete(client:T)(implicit ec:ExecutionContext):Future[Unit]
}

trait OauthClientFactory[I <: OauthClientInfo, T <: OauthClient] {
  def create(info: I)(implicit ec:ExecutionContext):Future[T]
}

class BasicOauthClientInfo(val redirectUri: Option[String] = None, val clientUri: Option[String] = None,
                           val description: Option[String] = None, val name:Option[String] = None,
                           val iconUri: Option[String] = None) extends OauthClientInfo

class BasicOauthClient(val id:String, val secret:String, val issuedAt:Long,
                       override val redirectUri: Option[String] = None, override val clientUri: Option[String] = None,
                       override val description: Option[String] = None, override val name:Option[String] = None,
                       override val iconUri: Option[String] = None) extends BasicOauthClientInfo with OauthClient

object BasicOauthClient {
  def apply(id: String, secret: String):BasicOauthClient = new BasicOauthClient(id, secret, new Date().getTime)
  def apply(id: String, secret: String, info:BasicOauthClientInfo):BasicOauthClient = new BasicOauthClient(id, secret, new Date().getTime,
    info.redirectUri, info.clientUri, info.description, info.name, info.iconUri)
}