import sbt._
import sbt.Keys._
import sbt.Tests.Setup

object BuildSettings {
  val projectName = "play-oauth"
  val buildVersion = "1.0.0-SNAPSHOT"

  val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := "fr.njin",
    version := buildVersion,
    scalaVersion := "2.10.2",
    crossScalaVersions := Seq("2.10.2"),
    crossVersion := CrossVersion.binary
  ) ++ Publish.settings 
    //++ ScctPlugin.instrumentSettings 
    //++ com.github.theon.coveralls.CoverallsPlugin.coverallsSettings
}

object Publish {
  object TargetRepository {
    def sonatype: Project.Initialize[Option[sbt.Resolver]] = version { (version: String) =>
      val nexus = "https://oss.sonatype.org/"
      if (version.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    }
  }
  lazy val settings = Seq(
    publishMavenStyle := true,
    publishTo <<= TargetRepository.sonatype,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    licenses := Seq("Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    homepage := Some(url("https://github.com/njin-fr/play-oauth")),
    pomExtra :=
      <scm>
        <url>git://github.com/njin-fr/play-oauth.git</url>
        <connection>scm:git://github.com/njin-fr/play-oauth.git</connection>
      </scm>
      <developers>
        <developer>
          <id>dbathily</id>
          <name>Didier Bathily</name>
        </developer>
      </developers>,
    credentials += {
      Seq("build.publish.user", "build.publish.password").map(k => Option(System.getProperty(k))) match {
        case Seq(Some(user), Some(pass)) =>
          Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", user, pass)
        case _                           =>
          Credentials(Path.userHome / ".ivy2" / ".credentials")
      }
    }
  )
}

object PlayOAuthBuild extends Build {
  import BuildSettings._

  //See http://stackoverflow.com/questions/7898273/how-to-get-logging-working-in-scala-unit-tests-with-testng-slf4s-and-logback
  val setupForSLF4J = Setup( cl =>
    cl.loadClass("org.slf4j.LoggerFactory").
      getMethod("getLogger",cl.loadClass("java.lang.String")).
      invoke(null,"ROOT")
  )

  lazy val commonResolvers = Seq(
    "Sonatype" at "http://oss.sonatype.org/content/groups/public/",
    "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/",
    "Typesafe repository releases" at "http://repo.typesafe.com/typesafe/releases/",
    "Typesafe repository snapshots" at "http://repo.typesafe.com/typesafe/snapshots/",
    "scct-github-repository" at "http://mtkopone.github.com/scct/maven-repo"
  )

  lazy val commonDependencies = Seq(
    "org.specs2" %% "specs2" % "2.1.1" % "test" cross CrossVersion.binary,
    //https://github.com/mtkopone/scct/issues/54
    "reaktor" %% "scct" % "0.2-SNAPSHOT" % "test"
  )

  lazy val playOAuthCommon = Project(
    projectName+"-common",
    file("common"),
    settings = buildSettings ++ Seq(
      resolvers := commonResolvers,
      libraryDependencies ++= commonDependencies ++ Seq(
        "org.slf4j" % "slf4j-api" % "1.7.5",
        "org.slf4j" % "jul-to-slf4j" % "1.7.5",
        "org.slf4j" % "jcl-over-slf4j" % "1.7.5",

        "ch.qos.logback" % "logback-core" % "1.0.13",
        "ch.qos.logback" % "logback-classic" % "1.0.13",

        "com.typesafe.play" %% "play-json" % "2.2.0"
      ),
      testOptions += setupForSLF4J
    )
  )

  lazy val playOAuth = Project(
    projectName,
    file("play-oauth"),
    settings = buildSettings ++ Seq(
      resolvers := commonResolvers,
      libraryDependencies ++= commonDependencies ++ Seq(
        "commons-validator" % "commons-validator" % "1.4.0",
        "com.typesafe.play" %% "play" % "2.2.0" cross CrossVersion.binary,
        "com.typesafe.play" %% "play-test" % "2.2.0" % "test" cross CrossVersion.binary,
        "com.github.theon" %% "scala-uri" % "0.4.0-SNAPSHOT" % "test"
      ),
      testOptions += setupForSLF4J
    )
  ).dependsOn(playOAuthCommon).aggregate(playOAuthCommon)
}

object PlayAuthServerBuild extends Build {
  import BuildSettings._

  val appName         = projectName+"-server"
  val appVersion      = buildVersion

  val appDependencies = Seq(
    "org.mindrot" % "jbcrypt" % "0.3m",

    "mysql" % "mysql-connector-java" % "5.1.18",
    "com.github.seratch"  %% "scalikejdbc-async" % "[0.2,)",
    "com.github.mauricio" %% "mysql-async"       % "[0.2,)",
    "com.github.seratch"  %% "scalikejdbc-async-play-plugin" % "[0.2,)",
    "com.github.tototoshi" %% "play-flyway" % "1.0.0-SNAPSHOT",

    "com.github.seratch" %% "scalikejdbc-test" % "[1.6,)"  % "test"
  )

  val main = play.Project(appName, appVersion, appDependencies, file("play-oauth-server")).settings(
    // Add your own project settings here
  ).dependsOn(PlayOAuthBuild.playOAuth, flywayPlugin).aggregate(flywayPlugin)

  lazy val flywayPlugin = Project (
    id = "flywayPlugin",
    base = file ("play-oauth-server/module/play-flyway/plugin"),
    settings = Defaults.defaultSettings ++ Seq (
      name := "play-flyway",
      organization := "com.github.tototoshi",
      version := "1.0.0-SNAPSHOT",
      resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
      libraryDependencies ++= Seq(
        "com.typesafe.play" %% "play" % "2.2.0" % "provided",
        "com.googlecode.flyway" % "flyway-core" % "2.1.1",
        "org.scalatest" %% "scalatest" % "1.9.1" % "test"
      ),
      scalacOptions ++= Seq("-language:_", "-deprecation")
    )

  )

}