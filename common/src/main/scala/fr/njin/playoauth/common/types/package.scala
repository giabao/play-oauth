package fr.njin.playoauth.common

/**
 * User: bathily
 * Date: 17/09/13
 */
package object types {

  sealed abstract class ParameterStyle(val name:String)
  case object Body extends ParameterStyle("body")
  case object Query extends ParameterStyle("query")
  case object Header extends ParameterStyle("header")

  sealed abstract class GrantedType(val name:String)
  case object AuthorizationCode extends GrantedType("authorization_code")
  case object Password extends GrantedType("password")
  case object RefreshToken extends GrantedType("refresh_token")
  case object ClientCredentials extends GrantedType("client_credentials")

  sealed abstract class ResponseType(val name:String)
  case object Code extends GrantedType("code")
  case object Token extends GrantedType("token")

  sealed abstract class TokenType(val name:String)
  case object Bearer extends GrantedType("Bearer")
  case object Mac extends GrantedType("MAC")

}
