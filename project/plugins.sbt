resolvers ++= Seq(
  Resolver.typesafeRepo("releases"),
  Classpaths.sbtPluginReleases
)

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")

addSbtPlugin("com.sqality.scct" % "sbt-scct" % "0.3")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.1.0")

addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.0.0")

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.4.1")

addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.3.3")

addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "0.8.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages" % "0.5.3")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.7.0")