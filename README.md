# Oauth2 kit for Play! Framework 2.2 [![Build Status](https://travis-ci.org/njin-fr/play-oauth.png?branch=master)](https://travis-ci.org/njin-fr/play-oauth) [![Coverage Status](https://coveralls.io/repos/njin-fr/play-oauth/badge.png)](https://coveralls.io/r/njin-fr/play-oauth)

play-oauth is a type safe and reactive framework which allows you to create an [Oauth2] (http://oauth.net) authorization server with Play Framework 2 in scala. You can also integrate an Oauth2 authorization relatively easily in your project.

It also allows you to protect your resources.

## Features

play-oauth implements the final version of the specification [http://tools.ietf.org/html/rfc6749] (http://tools.ietf.org/html/rfc6749). Amongst others the following aspects of the protocol:

- Authorization Grant
	- Authorization Code Grant
	- Implicit Grant
	- Resource Owner Password Credentials Grant
	- Client Credentials Grant
- Issuing an Access Token
- Refreshing an Access Token
- Accessing Protected Resources

Beyond the specification, play-oauth provided some helpers to protect the resources even if the authorization server is different from the resource server.

play-oauth is a fairly flexible framework

## Installation

Add the framework to your dependencies

```scala
"fr.njin" %% "play-oauth" % "1.0.0-SNAPSHOT"
```
 
## How to use it

The framework is responsible for implementing the specification but leaves you free to create your authorization server. You have to provide your models (the most difficult part) and to create 2 controller actions representing the endpoints of the protocol, Authorization endpoint and Token endpoint.

### Models

Apart `OauthResourceOwner`, the framework provides a base implementation for all of these models. You can use it as an example. Here is what you need to provide:

#### `OauthResourceOwner`

An entity capable of granting access to a protected resource. Maybe the User of your application.

> Example

```scala
case class User(username:String, 
				password:String, 
				permissions:Map[BasicOauthClient, BasicOAuthPermission[BasicOauthClient]]) extends OauthResourceOwner
```

#### `OauthClient`

An application making protected resource requests on behalf of the resource owner and with its authorization.

> Example

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

- `allowedResponseType` define the [allowed response types](http://tools.ietf.org/html/rfc6749#section-3.1.1) for this client: `code` and/or `token`
- `allowedGrantType` define the [allowed grant types](http://tools.ietf.org/html/rfc6749#section-4) for the client: `authorization_code` for authorization code, `refresh_token` for refreshing an Access Token,
   `password` for resource owner password credentials, and `client_credentials` for client credentials

#### `OauthCode`

The authorization code [http://tools.ietf.org/html/rfc6749#section-1.3.1](http://tools.ietf.org/html/rfc6749#section-1.3.1)

The authorization code is created for the customer according to the permission granted by the resource owner. These two entities are required in this model.

> Example

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

Represents the authorization granted by the resource owner when the client requests the authorization code or token.

> Example

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

> Example

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

> Example

```scala
class BasicOauthScope(val id: String,
                      val name: Option[String] = None,
                      val description: Option[String] = None) extends OauthScope
```

### Endpoints dependencies

Before implementing the endpoints, you will need to prepare their dependencies. Endpoints need to create some of the models above (codes and tokens) and generally find them. To do this, it will be necessary to provide a series of "repository" and some "factory".

#### `OauthCodeFactory`

Creates a `OauthCode` for the given resource owner and client. It is important to store the url redirection and scopes because they will be audited by the endpoint when generating the token.

> Beware, the created code must be persisted before being returned to the endpoint.

#### `OauthTokenFactory`

Creates a `OauthToken` for the given resource owner and client.

> Beware, the created token must be persisted before being returned to the endpoint.

#### `OauthClientRepository`

To retrieve a client from its identifier.

#### `OauthScopeRepository`

Provides the scopes for the id list. The returned map contains the available scopes. If a scope is not in the map, the endpoint will refuse to create the code or token requested by the client.

#### `OauthCodeRepository`

Provides a code from its value. Also allow to revoke a code before creating the token.

#### `OauthTokenRepository`

Provides a token from its value or from its refresh token value. Also allow to revoke a token. A token is revoked before a new one is created from its refresh token value. 

#### `OauthResourceOwnerPermission`

Provides to the authorization endpoint the permission granted by the resource owner in order to create a code or a token for the client.

### Endpoints

We now have what it takes to create our endpoint, the hardest has been done.

#### Authorization Endpoint

To create your authorization endpoint, instantiate an `AuthorizationEndpoint` and call the `authorize` method in your action.

```scala
def authorize(owner:(RequestHeader) => Option[RO])
             (onUnauthenticated:(AuthzRequest, C) => RequestHeader => Future[SimpleResult],
              onUnauthorized:(AuthzRequest, C) => RequestHeader => Future[SimpleResult])
             (implicit ec:ExecutionContext): RequestHeader => Future[SimpleResult]
```

with the following parameters:

- `owner:(RequestHeader) => Option[RO]` : A function that extract the current resource owner from the request. Return None if there is no resource owner.

- `onUnauthenticated:(AuthzRequest, C) => RequestHeader => Future[SimpleResult]` : A function called when no resource owner is found (when `owner` return None). You can use it to show a login page to the end user.

- `onUnauthorized:(AuthzRequest, C) => RequestHeader => Future[SimpleResult])` : A function called when the resource owner didn't allow the client to obtain a token (when `permissions` return None). You can use it to show a decision form to the end user then create a permission for the request.

```scala
def authz = Action.async { request =>
  new AuthorizationEndpoint(...).authorize(...)(..., ...).apply(request)
}
```

*See the sample for a feature-rich authorization endpoint [play-oauth-server/app/controllers/Authorization.scala](play-oauth-server/app/controllers/Authorization.scala)*

> **Please notice**
>
> The authorize endpoint don't use the request body. It's recommended that you pass an empty parser to your action `Action.async(parse.empty)`

#### Token Endpoint

To create your token endpoint, instantiate a `TokenEndpoint` and call the `token` method in your action after implementing the `authenticate` method. This method is use to authenticate the client of the request. See [Client Authentication section](#client-authentication) 

```scala
def token(owner: (String, String) => Future[Option[RO]], clientOwner: C => Future[Option[RO]])
         (implicit ec:ExecutionContext, writes: Writes[TokenResponse], errorWrites: Writes[OauthError]): Request[AnyContentAsFormUrlEncoded] => Future[SimpleResult]
```

with the following parameters:

- `owner: (String, String) => Future[Option[RO]]` : A function that return a resource owner for the given username & password pair. This is used for the *password* grant type
- `clientOwner: C => Future[Option[RO]]`: A function that return the resource owner of the given client. This is used for the *client credential* grant type

```scala
def token = Action.async(parse.urlFormEncoded.map(new AnyContentAsFormUrlEncoded(_))) { request =>
  new TokenEndpoint().token(...,...).apply(request)
}
```

*See the sample for a feature-rich token endpoint [play-oauth-server/app/controllers/Token.scala](play-oauth-server/app/controllers/Token.scala)*

> **Please notice**
>
> The token endpoint use a form url encoded request body. You have to pass the appropriate parser to your action `Action.async(parse.urlFormEncoded.map(new AnyContentAsFormUrlEncoded(_)))`

##### Client Authentication

In order to issue the token to the correct client, the token endpoint need to authenticate the client from the request. You will have to extend your endpoint with a `ClientAuthentication`

```scala
trait ClientAuthentication[C <: OauthClient] {
  def authenticate(request: Request[AnyContentAsFormUrlEncoded]): Future[Either[Option[C], OauthError]]
}
```

You can use the `SecretKeyClientAuthentication` which will bring to you the client id and secret key extracted from the request body.

Return either the client (none if not exist) or an error (ex : if credential don't match).

```scala
def authenticate(id: String, secret: String): Future[Either[Option[App], OauthError]] =
	clientRepository.find(id).map(_.fold[Either[Option[App], OauthError]](Left(None)) { app =>
	  if(app.secret == secret)
	    Left(Some(app))
	  else
	    Right(OauthError.InvalidClientError(Some(Messages(OAuth.ErrorClientCredentialsDontMatch))))
	})
```

### Resource protection

play-oauth gives you a helper to protect your resource. The `Oauth2Resource` object has a method `scoped` that you can compose with your action to get the resource owner for the request.

```scala
def scoped[U](scopes: String*)(action: U => EssentialAction)
             (onUnauthorized: EssentialAction = Action { Unauthorized("") },
              onForbidden: Seq[String] => EssentialAction = scopes => Action { Forbidden("") })
             (implicit resourceOwner: Seq[String] => RequestHeader => Future[Either[Option[U], Seq[String]]],
              ec: ExecutionContext = scala.concurrent.ExecutionContext.global): EssentialAction
```

This method extracts the resource owner from the request using the `resourceOwner` parameter. It is with this function that you extract the token from the request, verify the validity of the scope and get the user of the token. `Oauth2Resource` provides a function for this process, you just have to provide a way to retrieve the token value from the request (See some helpers in [play-oauth/src/main/scala/fr/njin/playoauth/Utils.scala](play-oauth/src/main/scala/fr/njin/playoauth/Utils.scala), particularly the `parseBearer` function) and to retrieve the token locally with your tokenRepository or remotely using a WS call.

See an example from the sample where we use `Oauth2Resource` to create `[Resource](play-oauth-server/app/domain/oauth2/Resource.scala)` object to protect the [API](play-oauth-server/app/controllers/API.scala)

##### Token info endpoint

If your Resource server differ to your Authorization server, you may need to bring a way for your Resource server to get information about a token. The token endpoint gives you a function for that. 

```scala
def info(token: String)
          (authenticate: RequestHeader => Future[Option[C]])
          (ok: TO => Future[SimpleResult])
          (onUnauthorized: Future[SimpleResult] = Future.successful(Unauthorized("")),
           onTokenNotFound: Future[SimpleResult] = Future.successful(NotFound("")),
           onForbidden: Future[SimpleResult] = Future.successful(Forbidden("")))
          (implicit ec: ExecutionContext): RequestHeader => Future[SimpleResult]
```

Use this to create an action in your Authorization server then in your Resource server, use the `remoteResourceOwner` (or the `basicAuthRemoteResourceOwner`) to retrieve the token for your protected resource.

* `authenticate: RequestHeader => Future[Option[C]]` extracts the client from the request. You can use the basic authentication parser from [play-oauth/src/main/scala/fr/njin/playoauth/Utils.scala](play-oauth/src/main/scala/fr/njin/playoauth/Utils.scala)
* `ok: TO => Future[SimpleResult]` the callback used to send the token.

> Example from the sample, in the controller `Token.scala`:

```scala
def info(value: String) = InTx { implicit tx =>
    Action.async { request =>
      new TokenEndpointController().info(value){ request =>
          Utils.parseBasicAuth(request).map{
            case (id, secret) => App.find(id).map(_.filter(_.secret == secret))
          }.getOrElse(Future.successful(None))
      } { token =>
        Future.successful(Ok(Json.toJson(token)))
      }().apply(request)
    }
  }
```

### i18n

> TODO

## Development

**sbt 0.13, scala 2.10.2 and play 2.2.0**

3 projects:

- *play-oauth-common* : Common dependencies, domain objects
- *play-oauth* : The framework, endpoints implementations and the resource helpers
- *play-oauth-server* The sample

To run the sample, you need a MySQL database.

- sbt
- project play-oauth-server
- play
- run 

Don't forget to edit the configuration file for your database settings.

Scaladoc

- sbt
- unidoc

The scala doc is generated in play-oauth/target/scala.../unidoc/




