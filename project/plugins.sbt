// Comment to get more information during initialization
logLevel := Level.Warn
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.5.4")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.2.0-M9")

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.6.0")
addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "5.1.0")