import sbt._
import sbt.Keys._
import sbtunidoc.Plugin._
import UnidocKeys._
import com.typesafe.sbt.SbtGit.{GitKeys => git}
import com.typesafe.sbt.SbtSite._
import com.typesafe.sbt.SbtGhPages._

val projectName = "play-oauth"
val buildVersion = "1.1.0-SNAPSHOT"
val playVersion = "2.4.1"

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishTo <<= version { (version: String) =>
    val nexus = "https://oss.sonatype.org/"
    if (version.trim.endsWith("SNAPSHOT"))
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
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
    </developers>
)

lazy val buildSettings = Seq(
  organization := "fr.njin",
  version := buildVersion,
  scalaVersion := "2.11.7",
  scalacOptions := Seq("-language:_", "-deprecation", "-unchecked", "-Xlint", "-feature"),
  crossScalaVersions := Seq("2.11.7"),
  autoAPIMappings := true
) ++ publishSettings

lazy val commonDependencies = Seq(
  "org.specs2" %% "specs2-core" % "3.3.1" % "test"
)

lazy val playOAuthCommon = (project in file("common"))
  .settings(buildSettings: _*)
  .settings(
    name := projectName + "-common",
    libraryDependencies ++= commonDependencies ++ Seq(
      "com.typesafe.play" %% "play-json" % playVersion
    )
  )

lazy val playOAuth = (project in file("play-oauth"))
  .settings(buildSettings: _*)
  .settings(
    name := projectName,
    libraryDependencies ++= commonDependencies ++ Seq(
      "commons-validator" % "commons-validator" % "1.4.1",
      "com.typesafe.play" %% "play" % playVersion,
      "com.typesafe.play" %% "play-ws" % playVersion,
      "com.typesafe.play" %% "play-specs2" % playVersion % "test",
      "com.typesafe.play" %% "play-test" % playVersion % "test",
      "com.netaporter" %% "scala-uri" % "0.4.7" % "test"
    )
  )
  .dependsOn(playOAuthCommon)
  .aggregate(playOAuthCommon)

lazy val root = project.in(file("."))
  .settings(buildSettings: _*)
  .settings(unidocSettings: _*)
  .settings(site.settings ++ ghpages.settings: _*)
  .settings(
    unidocProjectFilter in (ScalaUnidoc, unidoc) := inAnyProject -- inProjects(main),
    site.addMappingsToSiteDir(mappings in (ScalaUnidoc, packageDoc), "latest/api"),
    site.addMappingsToSiteDir(mappings in (ScalaUnidoc, packageDoc), buildVersion + "/api"),
    git.gitRemoteRepo := "git@github.com:njin-fr/play-oauth.git"
  )
  .settings(unidocSettings: _*)
  .aggregate(playOAuth)


lazy val main = (project in file("play-oauth-server"))
  .enablePlugins(PlayScala)
  .settings(
    name := projectName + "-server",
    version := buildVersion,
    organization := "fr.njin",
    scalaVersion := "2.11.7",
    scalacOptions := Seq("-language:_", "-deprecation", "-unchecked", "-Xlint", "-feature"),
    libraryDependencies ++= Seq(
      "de.svenkubiak"         % "jBCrypt"                         % "0.4",

      "mysql"                 % "mysql-connector-java"            % "5.1.35",
      "org.scalikejdbc"       %% "scalikejdbc-async"              % "0.5.5",
      "com.github.mauricio"   %% "mysql-async"                    % "0.2.16",
      "org.scalikejdbc"       %% "scalikejdbc-async-play-plugin"  % "0.5.5",
      "com.github.tototoshi"  %% "play-flyway"                    % "1.2.1",

      "org.scalikejdbc"       %% "scalikejdbc-test"               % "2.2.7"   % "test"
    )
  )
  .dependsOn(playOAuth)
