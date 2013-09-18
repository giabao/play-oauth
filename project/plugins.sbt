resolvers += "Sonatype" at "http://oss.sonatype.org/content/groups/public/"

resolvers += "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "scct-github-repository" at "http://mtkopone.github.com/scct/maven-repo"

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.5.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-pgp" % "0.8")

//addSbtPlugin("reaktor" % "sbt-scct" % "0.2-SNAPSHOT")

//addSbtPlugin("com.github.theon" %% "xsbt-coveralls-plugin" % "0.0.4")

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.2.0-RC1")