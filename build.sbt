import com.typesafe.sbt.SbtScalariform._

import scalariform.formatter.preferences._

name := """game-check-match"""

version := "0.1.0"

scalaVersion := "2.11.8"

resolvers += Resolver.jcenterRepo
resolvers += Resolver.url("Maven Central", url("http://central.maven.org/maven2/"))
resolvers += Resolver.sonatypeRepo("public")
resolvers ++= Seq(
  "anormcypher" at "http://repo.anormcypher.org/",
  "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"
)



libraryDependencies ++= Seq(
  "com.mohiva" %% "play-silhouette" % "4.0.0",
  "com.mohiva" %% "play-silhouette-password-bcrypt" % "4.0.0",
  "com.mohiva" %% "play-silhouette-persistence" % "4.0.0",
  "com.mohiva" %% "play-silhouette-crypto-jca" % "4.0.0",
  "org.webjars" %% "webjars-play" % "2.5.0-2",
  "net.codingwell" %% "scala-guice" % "4.0.1",
  "com.iheart" %% "ficus" % "1.2.6",
  "com.typesafe.play" %% "play-mailer" % "5.0.0",
  "com.enragedginger" %% "akka-quartz-scheduler" % "1.5.0-akka-2.4.x",
  "com.adrianhurt" %% "play-bootstrap" % "1.0-P25-B3",
  "com.mohiva" %% "play-silhouette-testkit" % "4.0.0" % "test",
  "com.lukaspradel" % "steam-web-api" % "1.2",
  "com.typesafe.akka" %% "akka-stream" % "2.4.17",
  "com.typesafe.akka" %% "akka-http" % "10.0.3",
  "com.websudos" %% "reactiveneo-dsl" % "0.3.0",
  "com.websudos" %% "reactiveneo-testing" % "0.3.0",
  "org.neo4j" % "neo4j-ogm" % "2.1.1",
  "org.neo4j.driver" % "neo4j-java-driver" % "1.1.2",
  "org.reactivemongo" %% "play2-reactivemongo" % "0.12.1",
  "com.mohiva" %% "play-silhouette-persistence-reactivemongo" % "4.0.1",
  specs2 % Test,
  cache,
  filters
)

//skip downloading dependencies on every compile
offline := false
updateOptions := updateOptions.value.withCachedResolution(true)

lazy val root = (project in file(".")).enablePlugins(PlayScala)

routesGenerator := InjectedRoutesGenerator

routesImport += "utils.route.Binders._"

scalacOptions ++= Seq(
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  "-feature", // Emit warning and location for usages of features that should be imported explicitly.
  "-unchecked", // Enable additional warnings where generated code depends on assumptions.
  "-Xfatal-warnings", // Fail the compilation if there are any warnings.
  "-Xlint", // Enable recommended additional warnings.
  "-Ywarn-adapted-args", // Warn if an argument list is modified to match the receiver.
  "-Ywarn-dead-code", // Warn when dead code is identified.
  "-Ywarn-inaccessible", // Warn about inaccessible types in method signatures.
  "-Ywarn-nullary-override", // Warn when non-nullary overrides nullary, e.g. def foo() over def foo.
  "-Ywarn-numeric-widen" // Warn when numerics are widened.
)

//********************************************************
// Scalariform settings
//********************************************************

defaultScalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(FormatXml, false)
  .setPreference(DoubleIndentClassDeclaration, false)
  .setPreference(DanglingCloseParenthesis, Preserve)

fork in run := false

initialCommands in console :=
  """
    |import com.lukaspradel.steamapi.webapi.client.SteamWebApiClient
    |import com.lukaspradel.steamapi.webapi.request.builders.SteamWebApiRequestFactory
    |import play.Configuration
    |import javax.inject.Inject
    |import scala.concurrent.duration._
    |import collection.JavaConverters._
    |import models.daos.SteamUserDAO.SteamId
    |import akka.stream.scaladsl._
    |import models.{ ServiceProfile, SteamProfile, SteamProfileFactory }
    |import ServiceProfile._
    |import akka.NotUsed
    |import akka.stream.{ OverflowStrategy, SourceShape }
    |import com.lukaspradel.steamapi.data.json.ownedgames.GetOwnedGames
    |import com.lukaspradel.steamapi.data.json.playersummaries.GetPlayerSummaries
    |import com.lukaspradel.steamapi.data.json.friendslist.GetFriendList
    |import models.Game.GameId
    |import utils.TimestampedFuture
    |import akka.actor.ActorSystem
    |import akka.stream.ActorMaterializer
    |import org.neo4j.graphdb.factory._
    |import org.neo4j.graphdb._
    |import models._
    |import daos._
    |import org.neo4j.driver.v1._
    |import org.neo4j.driver.v1.Values.parameters
    |val client = new SteamWebApiClient.SteamWebApiClientBuilder(key).build()
    |import scala.concurrent.ExecutionContext.Implicits.global
    |val steamProfileFactory = new models.SteamProfileFactoryImpl
    |def getUserSummaries(ids: List[SteamId]) = {
    |  val req = SteamWebApiRequestFactory.createGetPlayerSummariesRequest(ids.asJava)
    |  client.processRequest[GetPlayerSummaries](req)
    |}
    |def processSummaries(summaries: GetPlayerSummaries, steamProfileFactory: SteamProfileFactory): Seq[SteamProfile] = {
    |  summaries.getResponse.getPlayers.asScala.map {
    |    p: com.lukaspradel.steamapi.data.json.playersummaries.Player =>
    |      steamProfileFactory(p)
    |  }
    |}
    |def getOwnedGames(id: SteamId) = {
    |  val req = SteamWebApiRequestFactory.createGetOwnedGamesRequest(id, true, true, List[Integer]().asJava)
    |  client.processRequest[GetOwnedGames](req)
    |}
    |def processGames(games: GetOwnedGames) = {
    |    games.getResponse.getGames.asScala.map { g => (Game.fromApiModel(g), g.getPlaytimeForever.toInt) }
    |  }
    |def getFriends(id: SteamId) = {
    |    val req = SteamWebApiRequestFactory.createGetFriendListRequest(id)
    |    client.processRequest[GetFriendList](req)
    |  }
    |def processFriends(friends: GetFriendList) = {
    |    friends.getFriendslist.getFriends.asScala.map { f => (f.getSteamid, f.getFriendSince) }
    |  }
  """.stripMargin

fork in run := true

fork in run := true

fork in run := true

fork in run := true

fork in run := true

fork in run := true