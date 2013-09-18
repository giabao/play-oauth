# Redis Scala Support to Play! Framework 2.1 [![Build Status](https://travis-ci.org/njin-fr/play2-rediscala.png?branch=master)](https://travis-ci.org/njin-fr/play2-rediscala) [![Coverage Status](https://coveralls.io/repos/njin-fr/play2-rediscala/badge.png)](https://coveralls.io/r/njin-fr/play2-rediscala)

This is a plugin for Play 2.1, enabling support for [Rediscala](https://github.com/etaty/rediscala) - A Redis client for Scala (2.10+) and (AKKA 2.2+) with non-blocking and asynchronous I/O operations.

## How to use it

Add the plugin to your dependencies

```scala
"fr.njin" %% "play2-rediscala" % "1.0.0"
```

And declare it in conf/play.plugins

```
200:play.modules.rediscala.RedisPlugin
```

Get your client

```scala
val client = RedisPlugin.client()(app, Akka.system(app))
```

or

```scala
import play.api.Play.current
implicit val system = Akka.system

val client = RedisPlugin.client()
```

[Use it](https://github.com/etaty/rediscala)

## Configuration

Rediscala Plugin can handle multiple databases. You can configure them in your app configuration file `application.conf`

```
redis {
	# The default database.
	default {
		# You can use uri to configure a database
		uri: "redis://user:password@an.host.com:9092/"
	}
	mydb {
		host: localhost
		port: 6379
		user: ...
		password: ...
	}
	...
}
```

```scala
RedisPlugin.client() //Give you the default database client
RedisPlugin.client("mydb") //Give you 'mydb' database client
```

> Please Notice
> 
> Authentication is not handle by the plugin (yet?) so user and password key are useless for now


