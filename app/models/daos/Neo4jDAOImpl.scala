package models.daos

import javax.inject.Inject

import models.Game.GameId
import models.daos.SteamUserDAO.SteamId
import models.services.ProfileGraphService
import models.{ Game, SteamProfile }
import org.neo4j.graphdb.factory.GraphDatabaseFactory
import org.neo4j.graphdb.{ GraphDatabaseService, Label, RelationshipType }
import collection.JavaConverters._
import org.neo4j.driver.v1._
import org.neo4j.driver.v1.Values.parameters
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.{ Future, blocking }

//Driver driver = GraphDatabase.driver( "bolt://localhost:7687", AuthTokens.basic( "neo4j", "neo4j" ) );

/**
 * Created by henrik on 2017-03-04.
 */
class Neo4jDAOImpl @Inject() (graphDb: Driver) extends ProfileGraphService {

  def gameTimeTuple(game: com.lukaspradel.steamapi.data.json.ownedgames.Game) = (models.Game.fromApiModel(game), game.getPlaytimeForever)

  override def updateGames(user: SteamId, games: Seq[(Game, Int, Int)]): Unit = {
    if (games.size > GraphObjects.CYPHER_MAX) {
      throw new java.lang.IllegalArgumentException("Too many games for cypher statement")
    }
    val q1 = s"""MERGE (p:SteamProfile {id: "$user" }) """
    val query: String = games.foldRight(q1)((t: (Game, Int, Int), q: String) => {
      val g = t._1
      val playTimeForever = t._2
      val playTime2Weeks = t._3
      val gid = s"g${g.id}"
      q + s"""MERGE ($gid: Game {id: "${g.id}"}) SET ${gid}.name = "$g"
              |CREATE UNIQUE (p)-[r$gid :OWNS]-($gid)
              |SET r$gid.playTime = "$playTimeForever", r$gid.playTime2Weeks = "$playTime2Weeks" """.stripMargin
    })
    Future {
      blocking {
        val session = graphDb.session()
        val result = session.run(query)
        session.close()
        result
      }
    }
  }

  override def mergeProfile(user: SteamProfile): Unit = {
    val name = user.displayName.map(c => if (GraphObjects.escapeChars.contains(c)) GraphObjects.escapeChars(c) else c).mkString
    val query = s"""MERGE (p${user.id}: SteamProfile {id: "${user.id}"}) SET p${user.id}.name = "$name" """
    Future {
      blocking {
        val session = graphDb.session()
        val result = session.run(query)
        session.close()
        result
      }
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
      q +
        s"""MERGE (p$pid :SteamProfile {id: "$pid"})
         |CREATE UNIQUE (p)-[r:FRIEND]-(p$pid)
         |SET r.friendsSince = "$i"} """.stripMargin
    })
    println(s"neo4jdaoimpl:77 $query")
    Future {
      blocking {
        val session = graphDb.session()
        val result = session.run(query)
        session.close()
        result
      }
    }
  }

  override def getGames(user: SteamId): Future[Seq[(GameId, Int, Int)]] = {
    val (relationship: String, game: String, attribute1: String, attribute2) = ("o", "g", "playTime", "playTime2Weeks")
    val query = s"""match (n: SteamProfile {id: "$user"})-[$relationship :OWNS]-($game: Game) return $game, $relationship"""
    Future {
      blocking {
        println(query)
        val session = graphDb.session()
        val result = session.run(query).asScala
        session.close()
        result.map(res => {
          val t = parseRelationship(res, game, relationship, attribute1, attribute2)
          (t._1, t._2.head, t._2(1))
        }).toSeq
      }
    }
  }

  override def getFriends(user: SteamId): Future[Seq[(SteamId, Int)]] = {
    val (relationship: String, friend: String, attribute: String) = ("r", "f", "friendsSince")
    val query = s"""match (n: SteamProfile {id: "$user"})-[$relationship :FRIEND]-($friend: SteamProfile) return $friend, $relationship"""
    Future {
      blocking {
        println(query)
        val session = graphDb.session()
        val result = session.run(query).asScala
        session.close()
        result.map(res => {
          val t = parseRelationship(res, friend, relationship, attribute)
          (t._1, t._2.head)
        }).toSeq
      }
    }
  }

  private def parseRelationship(record: Record, node: String, relationship: String, attributes: String*): (String, Seq[Int]) = {
    val valueMap = record.fields.asScala
      .map(pair => pair.key -> pair.value)
      .toMap[String, Value]
    val id = valueMap(node).get("id").asString
    val attributeValues = attributes.map(attribute => valueMap(relationship).get(attribute).asString.toInt)
    println(id + " " + attributeValues)
    (id, attributeValues)
  }

  override def recentMutualPlayTime(user: SteamId): Future[Seq[(SteamId, GameId)]] = {
    val (matchingProfile, game, path) = ("n2", "g", "p")
    val query = s"""match (n: SteamProfile {id: "$user"})-[r1:OWNS]-($game)-[r2:OWNS]-($matchingProfile)
where n <> $matchingProfile
optional match $path = shortestPath((n)-[:FRIEND*1..2]-($matchingProfile))
with toInt(r1.playTime2Weeks) * toInt(r2.playTime2Weeks) as t,$game,$matchingProfile,$path
where t > 0
return $matchingProfile, $game, $path
order by t desc
limit 10"""
    Future {
      blocking {
        println(query)
        val session = graphDb.session()
        val result = session.run(query).asScala
        session.close()
        result.map(res => {
          val g: GameId = res.get(game).asNode().get("id").asString()
          val mp: SteamId = res.get(matchingProfile).asNode().get("id").asString()
          /*val p: Option[Iterable[SteamId]] = {
          Option(res.get(path)).map{ value =>
            value.asPath().nodes().asScala.map(_.get("id").asString())
          }
        }
        (g, mp, p)*/
          (mp, g)
        }).toSeq
      }
    }
  }

  override def mutualTotalGameTime(user: SteamId): Future[Seq[(SteamId, GameId)]] = {
    val (matchingProfile, game, path) = ("n2", "g", "p")
    val query = s"""match (n: SteamProfile {id: "$user"})-[r1:OWNS]-($game)-[r2:OWNS]-($matchingProfile)
where n <> $matchingProfile
optional match $path = shortestPath((n)-[:FRIEND*1..2]-($matchingProfile))
with toInt(r1.playTime)^2 * toInt(r2.playTime) as t,$game,$matchingProfile,$path
where t > 0
return $matchingProfile, $game, $path
order by t desc
limit 10"""
    Future {
      blocking {
        println(query)
        val session = graphDb.session()
        val result = session.run(query).asScala
        session.close()
        result.map(res => {
          val g: GameId = res.get(game).asNode().get("id").asString()
          val mp: SteamId = res.get(matchingProfile).asNode().get("id").asString()
          /*val p: Option[Iterable[SteamId]] = {
          Option(res.get(path)).map{ value =>
            value.asPath().nodes().asScala.map(_.get("id").asString())
          }
        }
        (g, mp, p)*/
          (mp, g)
        }).toSeq
      }
    }
  }

  override def trendingGames(amount: Int): Future[Seq[GameId]] = {
    val game: String = "g"
    val query = s"""match ($game :Game)-[r :OWNS]-()
with $game, collect({recent_time: toFloat(r.playTime2Weeks), total_time: toFloat(r.playTime)}) AS time_list
return g, reduce(tot = 0, play_times in time_list |
	case
    	when play_times.recent_time = 0.0 then tot""" + //this covers all cases  of total time == 0, so avoids div0
      // and if recent time == 0 then the result will be 0 in all other cases anyways
      """      else tot + play_times.recent_time^2 / (play_times.total_time)
end) / sqrt(size(time_list)) AS trending""" + //heuristic designed to heavily promote recent play time,
      // but more so if it's a game that isn't already very popular. also gives
      // some weight to the number of people playing, since we divide by the sqrt instead of calculating the mean
      s"""
order by trending desc
limit $amount"""
    Future {
      blocking {
        println(query)
        val session = graphDb.session()
        val result = session.run(query).asScala
        session.close()
        result.map(res =>
          res.get(game).asNode().get("id").asString()
        ).toSeq
      }
    }
  }

  override def popularAmongFriends(id: SteamId, amount: Int): Future[Seq[GameId]] = {
    val gameId = "gid"
    val query = s"""MATCH (n :SteamProfile {id: "$id"})-[:FRIEND]-(f)-[r3 :OWNS]-(recommendation)
                  |WHERE toInt(r3.playTime) > 0
                  |RETURN sum(toInt(r3.playTime2Weeks)) AS weight, recommendation.id AS $gameId
                  |ORDER BY weight DESC
                  |LIMIT $amount""".stripMargin
    Future {
      blocking {
        println(query)
        val session = graphDb.session()
        val result = session.run(query).asScala
        session.close()
        result.map(res =>
          res.get(gameId).asString()
        ).toSeq
      }
    }
  }

  override def peopleLikeYou(id: SteamId, amount: Int): Future[Seq[SteamId]] = {
    val `match` = "pid"
    val query = s"""MATCH (n :SteamProfile {id: "$id"})-[r1 :OWNS]-()-[r2 :OWNS]-(smlr)
                   |WHERE not(n = smlr) and toInt(r1.playTime) > 0 and toInt(r2.playTime) > 0
                   |WITH smlr.id AS ${`match`}, sum(toFloat(r1.playTime)^2 * toFloat(r2.playTime)) AS similarity
                   |RETURN ${`match`}
                   |ORDER BY similarity DESC
                   |LIMIT $amount""".stripMargin
    Future {
      blocking {
        println(query)
        val session = graphDb.session()
        val result = session.run(query).asScala
        session.close()
        result.map(res =>
          res.get(`match`).asString()
        ).toSeq
      }
    }
  }

  override def popularAmongPeopleLikeYou(id: SteamId, amount: Int): Future[Seq[GameId]] = {
    val gameId = "gid"
    val query = s"""MATCH (n :SteamProfile {id: "$id"})-[r1 :OWNS]-()-[r2 :OWNS]-(smlr)
      |WHERE not(n = smlr) and toInt(r1.playTime) > 0 and toInt(r2.playTime) > 0
      |WITH smlr, sum(toFloat(r1.playTime)^2 * toFloat(r2.playTime)) AS similarity
      |MATCH (smlr)-[r3 :OWNS]-(recommendation)
      |WHERE toInt(r3.playTime) > 0
      |RETURN sum(similarity * toFloat(r3.playTime)) AS weight, recommendation.id AS $gameId
      |ORDER BY weight DESC
      |LIMIT $amount""".stripMargin
    Future {
      blocking {
        println(query)
        val session = graphDb.session()
        val result = session.run(query).asScala
        session.close()
        result.map(res =>
          res.get(gameId).asString()
        ).toSeq
      }
    }
  }

  override def friendsWithGame(id: SteamId, game: GameId): Future[Seq[SteamId]] = {
    val friend: String = "f"
    val query = s"""match (g :Game {id: "$game"})-[r :OWNS]-($friend: SteamProfile)-[:FRIEND]-(n: SteamProfile {id: "$id"})
return $friend"""
    Future {
      blocking {
        println(query)
        val session = graphDb.session()
        val result = session.run(query).asScala
        session.close()
        result.map(res =>
          res.get(friend).asNode().get("id").asString()
        ).toSeq
      }
    }
  }

  override def filterHasGame(steamUsers: Seq[SteamId], gameId: GameId): Future[Seq[SteamId]] = {
    val profileId: String = "pid"
    val filtered: String = "res"
    val listString = steamUsers.tail.foldLeft(steamUsers.head)((tail, head) => s""" "$head", $tail""")
    val query = s"""UNWIND([$listString]) AS $profileId
                    |MATCH (n :SteamProfile {id: $profileId})-[:OWNS]-(g :Game {id: "$gameId"})
                    |RETURN n.id AS $filtered """.stripMargin
    Future {
      blocking {
        println(query)
        val session = graphDb.session()
        val result = session.run(query).asScala
        session.close()
        result.map(res =>
          res.get(filtered).asString()
        ).toSeq
      }
    }
  }

  override def mutualFriends(users: Seq[SteamId]): Future[Seq[SteamId]] = {
    val profileId: String = "pid"
    val mutual: String = "res"
    println(s"Neo4jDAOImpl.scala:326 $users")
    val listString = users.tail.foldLeft(s"""MATCH (:SteamProfile {id: "${users.head}" })-[:FRIEND]-(f :SteamProfile)""")((tail, head) => s"""$tail WHERE (f)-[:FRIEND]-(:SteamProfile {id: "$head" })""")
    println(s"Neo4jDAOImpl.scala:328 $listString")
    val query = s"""$listString
                   |RETURN f.id AS $mutual""".stripMargin
    Future {
      blocking {
        println(query)
        val session = graphDb.session()
        val result = session.run(query).asScala
        session.close()
        result.map(res =>
          res.get(mutual).asString()
        ).toSeq
      }
    }
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
