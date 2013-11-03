package fr.njin.playoauth.common.domain

import scala.concurrent.Future

/**
 * User: bathily
 * Date: 17/09/13
 */

trait OauthClientInfo {

}

trait OauthClient extends OauthClientInfo {
  def id:String
  def secret:String
  def allowedResponseType: Seq[String]
  def allowedGrantType: Seq[String]
  def redirectUris: Option[Seq[String]]
  def redirectUri:Option[String]
  def authorized: Boolean
}

trait OauthClientRepository[T <: OauthClient] {
  def find(id:String):Future[Option[T]]
}

class BasicOauthClient(val id:String,
                       val secret:String,
                       val allowedResponseType: Seq[String],
                       val allowedGrantType: Seq[String],
                       val redirectUris: Option[Seq[String]] = None,
                       val authorized: Boolean = true) extends OauthClient {
  def redirectUri: Option[String] = redirectUris.flatMap(_.headOption)
}