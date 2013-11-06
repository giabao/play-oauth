package fr.njin.playoauth.common.domain

import scala.concurrent.Future

/**
 * Represents the oauth2 client
 */
trait OauthClient {

  /**
   * @return the client id
   */
  def id:String

  /**
   * @return the client secret key
   */
  def secret:String

  /**
   * @return the allowed response types for the client.
   * See [[fr.njin.playoauth.common.OAuth.ResponseType]] for the available response types.
   */
  def allowedResponseType: Seq[String]

  /**
   * @return the allowed grant types for the client.
   * See [[fr.njin.playoauth.common.OAuth.GrantType]] for the available grant types.
   */
  def allowedGrantType: Seq[String]

  /**
   * @return the urls registered by the client.
   * The Authorization endpoint will only redirect to one of these urls.
   *
   * If the client's request don't mention a redirectUri, the authorization endpoint
   * will redirect to the value returned by [[redirectUri]]
   */
  def redirectUris: Option[Seq[String]]

  /**
   * @return the url to which the authorization endpoint will redirect if
   * the client don't specify a redirect url in its request.
   */
  def redirectUri:Option[String]

  /**
   * @return true if the client is authorized to request a token
   */
  def authorized: Boolean
}

/**
 * Repository used to retrieve a client by its id
 * @tparam T Type of the client
 */
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