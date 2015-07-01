import sbt._
import sbt.Keys._
import sbtunidoc.Plugin._
import UnidocKeys._
import com.typesafe.sbt.SbtGit.{GitKeys => git}
import com.typesafe.sbt.SbtSite._
import com.typesafe.sbt.SbtGhPages._
import play.core.PlayVersion.{current => playVersion}

val buildVersion = "1.1.0-SNAPSHOT"

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

lazy val `play-oauth-common` = (project in file("common"))
  .settings(buildSettings: _*)
  .settings(
    libraryDependencies ++= commonDependencies ++ Seq(
      "com.typesafe.play" %% "play-json" % playVersion
    )
  )

lazy val `play-oauth` = (project in file("play-oauth"))
  .settings(buildSettings: _*)
  .settings(
    libraryDependencies ++= commonDependencies ++ Seq(
      "commons-validator" % "commons-validator" % "1.4.1", //FIXME remove?
      "com.typesafe.play" %% "play" % playVersion,
      "com.typesafe.play" %% "play-ws" % playVersion,
      "com.typesafe.play" %% "play-specs2" % playVersion % "test",
      "com.typesafe.play" %% "play-test" % playVersion % "test",
      "com.netaporter"    %% "scala-uri" % "0.4.7" % "test"
    )
  )
  .dependsOn(`play-oauth-common`)
  .aggregate(`play-oauth-common`)

lazy val root = project.in(file("."))
  .settings(buildSettings: _*)
  .settings(unidocSettings: _*)
  .settings(site.settings ++ ghpages.settings: _*)
  .settings(
    unidocProjectFilter in (ScalaUnidoc, unidoc) := inAnyProject,
    site.addMappingsToSiteDir(mappings in (ScalaUnidoc, packageDoc), "latest/api"),
    site.addMappingsToSiteDir(mappings in (ScalaUnidoc, packageDoc), buildVersion + "/api"),
    git.gitRemoteR
      epo := "git@github.com:njin-fr/play-oauth.git"
  )
  .settings(unidocSettings: _*)
  .aggregate(`play-oauth`)
