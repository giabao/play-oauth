resolvers ++= Seq(
  Classpaths.typesafeReleases,
  Classpaths.typesafeSnapshots,
  "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/",
  "scct-github-repository" at "http://mtkopone.github.com/scct/maven-repo"
)

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.5.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-pgp" % "0.8")

addSbtPlugin("com.github.scct" % "sbt-scct" % "0.2.1")

addSbtPlugin("com.github.theon" %% "xsbt-coveralls-plugin" % "0.0.5-SNAPSHOT")

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.2.0")