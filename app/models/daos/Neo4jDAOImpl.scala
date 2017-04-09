package models.daos

import javax.inject.Inject

import models.Game.GameId
import models.daos.GraphObjects.SteamProfile
import models.daos.SteamUserDAO.SteamId
import models.services.ProfileGraphService
import models.{ Game, SteamProfile }
import org.neo4j.graphdb.factory.GraphDatabaseFactory
import org.neo4j.graphdb.{ GraphDatabaseService, Label, RelationshipType }
import collection.JavaConverters._
import scala.util.Try
import org.neo4j.driver.v1._
import org.neo4j.driver.v1.Values.parameters

//Driver driver = GraphDatabase.driver( "bolt://localhost:7687", AuthTokens.basic( "neo4j", "neo4j" ) );

/**
 * Created by henrik on 2017-03-04.
 */
class Neo4jDAOImpl @Inject() (graphDb: Driver) extends ProfileGraphService {

  def updateGames(user: SteamProfile, games: Seq[(Game, Int)]) = {
    val q1 = s"""MERGE (p:SteamProfile {id: "${user.id}", name: "${user.displayName}" }) """
    val query: String = games.foldRight(q1)((t: (Game, Int), q: String) => {
      val g = t._1
      val playTime = t._2
      val gid = s"g${g.id}"
      q + s"""MERGE ($gid: Game {id: "${g.id}", name: "$g"}) MERGE (p)-[:OWNS { playTime: "$playTime" } ]-($gid) """
    })
    Try {
      val session = graphDb.session()
      val result = session.run(query)
      session.close()
      result
    }
  }

  def gameTimeTuple(game: com.lukaspradel.steamapi.data.json.ownedgames.Game) = (models.Game.fromApiModel(game), game.getPlaytimeForever)

  def updateFriends(user: SteamProfile, friends: Seq[(SteamProfile, Int)]) = {
    val q1 = s"""MERGE (p:SteamProfile {id: "${user.id}", name: "${user.displayName}" }) """
    val query: String = friends.foldRight(q1)((t: (SteamProfile, Int), q: String) => {
      val p = t._1
      val i = t._2
      val pid = s"p${p.id}"
      q + s"""MERGE ($pid :SteamProfile {id: "${p.id}", name: "${p.displayName}" }) MERGE (p)-[:FRIEND {friendsSince: "$i"}]-($pid) """
    })
    Try {
      val session = graphDb.session()
      val result = session.run(query)
      session.close()
      result
    }
  }

  override def updateGames(user: SteamId, games: Seq[(Game, Int)]): Unit = {
    if (games.size > GraphObjects.CYPHER_MAX) {
      throw new java.lang.IllegalArgumentException("Too many games for cypher statement")
    }
    val q1 = s"""MERGE (p:SteamProfile {id: "$user" }) """
    val query: String = games.foldRight(q1)((t: (Game, Int), q: String) => {
      val g = t._1
      val playTime = t._2
      val gid = s"g${g.id}"
      q + s"""MERGE ($gid: Game {id: "${g.id}"}) SET ${gid}.name = "$g" MERGE (p)-[:OWNS { playTime: "$playTime" } ]-($gid) """
    })
    Try {
      val session = graphDb.session()
      val result = session.run(query)
      session.close()
      result
    }
  }

  override def mergeProfile(user: SteamProfile): Unit = {
    val name = user.displayName.map(c => if (GraphObjects.escapeChars.contains(c)) GraphObjects.escapeChars(c) else c).mkString
    val query = s"""MERGE (p${user.id}: SteamProfile {id: "${user.id}"}) SET p${user.id}.name = "$name" """
    Try {
      val session = graphDb.session()
      val result = session.run(query)
      session.close()
      result
    }
  }

  override def updateFriends(user: SteamId, friends: Seq[(SteamId, Int)]): Unit = {
    if (friends.size > GraphObjects.CYPHER_MAX) {
      throw new java.lang.IllegalArgumentException("Too many friends for cypher statement")
    }
    val q1 = s"""MERGE (p:SteamProfile {id: "$user"}) """
    val query: String = friends.foldRight(q1)((t: (SteamId, Int), q: String) => {
      val pid = t._1
      val i = t._2
      q + s"""MERGE (p$pid :SteamProfile {id: "$pid"}) MERGE (p)-[:FRIEND {friendsSince: "$i"}]-(p$pid) """
    })
    Try {
      val session = graphDb.session()
      val result = session.run(query)
      session.close()
      result
    }
  }

  override def getGames(user: SteamId): Try[Seq[(GameId, Int)]] = {
    val (relationship: String, game: String, attribute: String) = ("o", "g", "playTime")
    val query = s"""match (n: SteamProfile {id: "$user"})-[$relationship :OWNS]-($game: Game) return $game, $relationship"""
    Try {
      println(query)
      val session = graphDb.session()
      val result = session.run(query).asScala
      session.close()
      result.map(parseRelationship(_, game, relationship, attribute)).toSeq
    }
  }

  override def getFriends(user: SteamId): Try[Seq[(SteamId, Int)]] = {
    val (relationship: String, friend: String, attribute: String) = ("r", "f", "friendsSince")
    val query = s"""match (n: SteamProfile {id: "$user"})-[$relationship :FRIEND]-($friend: SteamProfile) return $friend, $relationship"""
    Try {
      println(query)
      val session = graphDb.session()
      val result = session.run(query).asScala
      session.close()
      result.map(parseRelationship(_, friend, relationship, attribute)).toSeq
    }
  }

  private def parseRelationship(record: Record, node: String, relationship: String, attribute: String): (String, Int) = {
    val valueMap = record.fields.asScala
      .map(pair => pair.key -> pair.value)
      .toMap[String, Value]
    val id = valueMap(node).get("id").asString
    val attributeValue = valueMap(relationship).get(attribute).asString
    println(id + " " + attributeValue)
    (id, attributeValue.toInt)
  }
}

object GraphObjects {
  val CYPHER_MAX = 70
  val escapeChars = Map('\t' -> "\\t", '\b' -> "\\b", '\n' -> "\\n", '\r' -> "\\r", '\f' -> "\\f", '\'' -> "\\'", '"' -> "\\\"", '\\' -> "\\\\")
  case object KNOWS extends RelationshipType {
    override def name(): String = "KNOWS"
  }
  case object OWNS extends RelationshipType {
    override def name(): String = "OWNS"
  }
  case object SteamProfile extends Label {
    override def name(): String = "SteamProfile"
  }
  case object Game extends Label {
    override def name(): String = "Game"
  }
}
