import sbt._
import sbt.Keys._
import sbtunidoc.Plugin._
import UnidocKeys._
import com.typesafe.sbt.SbtGit.{GitKeys => git}
import com.typesafe.sbt.SbtSite._
import com.typesafe.sbt.SbtGhPages._
import play.sbt.PlayImport.{component => playModule}

val buildVersion = "2.0.0-SNAPSHOT"

lazy val buildSettings = Seq(
  organization := "com.sandinh",
  version := buildVersion,
  scalaVersion := "2.11.7",
  scalacOptions := Seq("-language:_", "-deprecation", "-unchecked", "-Xlint", "-feature"),
  crossScalaVersions := Seq("2.11.7"),
  resolvers += Resolver.bintrayRepo("scalaz", "releases"),
  autoAPIMappings := true,
  publishTo := {
    if (isSnapshot.value)
      (sys.props.get("publish-to").fold[Option[Resolver]]
        (Some(Resolver.defaultLocal))
        ({url => Some("Local Realm" at url)}))
    else
      Some(Resolver.typesafeRepo("releases"))
  }
)

lazy val commonDependencies = Seq(
  "org.specs2" %% "specs2-core" % "3.3.1" % "test"
)

lazy val `play-oauth-common` = (project in file("common"))
  .settings(buildSettings: _*)
  .settings(
    libraryDependencies ++= commonDependencies :+ json
  )

lazy val `play-oauth` = (project in file("play-oauth"))
  .settings(buildSettings: _*)
  .settings(
    libraryDependencies ++= commonDependencies ++ Seq(
      "commons-validator" % "commons-validator" % "1.4.1", //FIXME remove?
      playModule("play"), ws,
      specs2 % "test",
      "com.netaporter"    %% "scala-uri" % "0.4.7" % "test"
    )
  )
  .dependsOn(`play-oauth-common`)
  .aggregate(`play-oauth-common`)

lazy val `play-oauth-root` = project.in(file("."))
  .settings(buildSettings: _*)
  .settings(unidocSettings: _*)
  .settings(site.settings ++ ghpages.settings: _*)
  .settings(
    unidocProjectFilter in (ScalaUnidoc, unidoc) := inAnyProject,
    site.addMappingsToSiteDir(mappings in (ScalaUnidoc, packageDoc), "latest/api"),
    site.addMappingsToSiteDir(mappings in (ScalaUnidoc, packageDoc), buildVersion + "/api"),
    git.gitRemoteRepo := "git@github.com:giabao/play-oauth.git"
  )
  .settings(unidocSettings: _*)
  .aggregate(`play-oauth`)

