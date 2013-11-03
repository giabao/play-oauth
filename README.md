# Oauth2 kit for Play! Framework 2.2 [![Build Status](https://travis-ci.org/njin-fr/play-oauth.png?branch=master)](https://travis-ci.org/njin-fr/play-oauth) [![Coverage Status](https://coveralls.io/repos/njin-fr/play-oauth/badge.png)](https://coveralls.io/r/njin-fr/play-oauth)

play-oauth est un framework qui vous permettra de créer un serveur d'authorisation [Oauth2](http://oauth.net) avec Play Framework 2 en scala. Vous pourrez aussi intégrer une authorisation Oauth2 relativement facilement dans votre projet mais aussi protéger vos ressources.

## Features

play-oauth implémente la version finale de la spécification [http://tools.ietf.org/html/rfc6749](http://tools.ietf.org/html/rfc6749). Entre autres les aspects suivants du protocole: 

- Authorization Grant
	- Authorization Code Grant
	- Implicit Grant
	- Resource Owner Password Credentials Grant
	- Client Credentials Grant
- Issuing an Access Token
- Refreshing an Access Token
- Accessing Protected Resources

Au délà de la spécification, play-oauth fourni quelques helpers pour protéger les ressources. Que çà soit pour les ressources qui résident avec le serveur d'authorisation ou pour celles qui sont distantes.

## Installation

Ajouter le framework à vos dépendances

```scala
"fr.njin" %% "play-oauth" % "1.0.0-SNAPSHOT"
```
 
## How to use it

Le framework se charge d'implémenter la spécification mais vous laisse libre de créer votre serveur d'authorisation. Vous aurez à fournir vos modèles (çà reste la partie la plus compliquée) et à créer les 2 actions réprésentants les endpoints du protocoles, Authorization endpoint et Token endpoint.

### Models

Hormis `OauthResourceOwner`, le framework fournis une implémentation de base de pour tous ces modèles. Vous pourrez vous en servir comme exemple. Voici donc ce que vous aurez à fournir :

#### `OauthResourceOwner`

An entity capable of granting access to a protected resource. Maybe the User of your application.

Exemple

```scala
case class User(username:String, 
				password:String, 
				permissions:Map[BasicOauthClient, BasicOAuthPermission[BasicOauthClient]]) extends OauthResourceOwner
```

#### `OauthClient`

An application making protected resource requests on behalf of the resource owner and with its authorization.

- `allowedResponseType` définie les [response types authorisés](http://tools.ietf.org/html/rfc6749#section-3.1.1) pour ce client : `code` et/ou `token`
- `allowedGrantType` définie les [grant types authorisés](http://tools.ietf.org/html/rfc6749#section-4) pour le client : `authorization_code` for authorization code, `refresh_token` for refreshing an Access Token,
   `password` for resource owner password credentials, and `client_credentials` for client credentials

Exemple

```scala
class BasicOauthClient(val id:String,
                       val secret:String,
                       val allowedResponseType: Seq[String],
                       val allowedGrantType: Seq[String],
                       val redirectUris: Option[Seq[String]] = None,
                       val authorized: Boolean = true) extends OauthClient {
                       
  def redirectUri: Option[String] = redirectUris.flatMap(_.headOption)

}
```

#### `OauthCode`

The authorization code [http://tools.ietf.org/html/rfc6749#section-1.3.1](http://tools.ietf.org/html/rfc6749#section-1.3.1)

Le code d'authorisation est créé pour le client selon la permission accordée par le resource owner. Ces 2 entités sont donc nécessaire dans ce modèle.

Exemple

```scala
class BasicOauthCode[RO <: OauthResourceOwner, C <: OauthClient]
                    (val value: String,
                     val owner:RO,
                     val client: C,
                     val issueAt: Long,
                     val expireIn: Long = OAuth.MaximumLifetime.toMillis,
                     val revoked: Boolean = false,
                     val redirectUri: Option[String] = None,
                     val scopes: Option[Seq[String]] = None) extends OauthCode[RO, C]
```

#### `OauthPermission`

Représente l'authorisation donnée par le resource owner lorsque le client demande le code d'authorization.

Exemple

```scala
class BasicOAuthPermission[C <: OauthClient](val accepted: Boolean,
                                             val client: C,
                                             val scope: Option[Seq[String]],
                                             val redirectUri: Option[String]) extends OauthPermission[C] {

  def authorized(request: AuthzRequest): Boolean = accepted && request.redirectUri == redirectUri

}
```

#### `OauthToken`

Access tokens [http://tools.ietf.org/html/rfc6749#section-1.4](http://tools.ietf.org/html/rfc6749#section-1.4)

Exemple

```scala
class BasicOauthToken[RO <: OauthResourceOwner, C <: OauthClient](
                      val owner: RO,
                      val client: C,
                      val accessToken: String,
                      val tokenType: String,
                      val revoked: Boolean = false,
                      val expiresIn: Option[Long] = None,
                      val refreshToken: Option[String] = None,
                      val scope: Option[Seq[String]] = None) extends OauthToken[RO, C]
```

#### `OauthScope`

Access Token Scope [http://tools.ietf.org/html/rfc6749#section-3.3](http://tools.ietf.org/html/rfc6749#section-3.3)

Exemple

```scala
class BasicOauthScope(val id: String,
                      val name: Option[String] = None,
                      val description: Option[String] = None) extends OauthScope
```

### Endpoints




> Please Notice
> 
> Authentication is not handle by the plugin (yet?) so user and password key are useless for now


