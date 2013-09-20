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
  def authorized: Boolean
}

trait OauthClient extends OauthClientInfo {
  def id:String
  def secret:String
  def allowedResponseType: Seq[String]
  def issuedAt:Long
}

trait OauthClientRepository[T <: OauthClient] {
  def find(id:String)(implicit ec:ExecutionContext):Future[Option[T]]
  def save(client:T)(implicit ec:ExecutionContext):Future[T]
  def delete(client:T)(implicit ec:ExecutionContext):Future[Unit]
}

trait OauthClientFactory[I <: OauthClientInfo, T <: OauthClient] {
  def apply(allowedResponseType: Seq[String], info: I)(implicit ec:ExecutionContext):Future[T]
}

class BasicOauthClientInfo(val redirectUri: Option[String] = None, val clientUri: Option[String] = None,
                           val description: Option[String] = None, val name:Option[String] = None,
                           val iconUri: Option[String] = None, val authorized: Boolean = true) extends OauthClientInfo

class BasicOauthClient(val id:String, val secret:String, val allowedResponseType: Seq[String], val issuedAt:Long,
                       override val redirectUri: Option[String] = None, override val clientUri: Option[String] = None,
                       override val description: Option[String] = None, override val name:Option[String] = None,
                       override val iconUri: Option[String] = None, override val authorized: Boolean = true) extends BasicOauthClientInfo with OauthClient

object BasicOauthClient {
  def apply(id: String, secret: String, allowedResponseType: Seq[String]):BasicOauthClient = new BasicOauthClient(id, secret, allowedResponseType, new Date().getTime)
  def apply(id: String, secret: String, allowedResponseType: Seq[String], info:BasicOauthClientInfo):BasicOauthClient = new BasicOauthClient(id, secret, allowedResponseType, new Date().getTime,
    info.redirectUri, info.clientUri, info.description, info.name, info.iconUri, info.authorized)
}