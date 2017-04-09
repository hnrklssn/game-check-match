package models.daos

import java.time.LocalDateTime

import com.lukaspradel.steamapi.webapi.client.SteamWebApiClient
import com.lukaspradel.steamapi.webapi.request.builders.SteamWebApiRequestFactory
import play.Configuration
import javax.inject.Inject

import scala.concurrent.duration._
import collection.JavaConverters._
import models.daos.SteamUserDAO.SteamId
import akka.stream.scaladsl._
import models.{ Game, ServiceProfile, SteamProfile, SteamProfileFactory }
import ServiceProfile._
import akka.NotUsed
import akka.stream.{ OverflowStrategy, SourceShape }
import com.lukaspradel.steamapi.data.json.friendslist.GetFriendList
import com.lukaspradel.steamapi.data.json.ownedgames.GetOwnedGames
import com.lukaspradel.steamapi.data.json.playersummaries.GetPlayerSummaries
import models.Game.GameId
import utils.TimestampedFuture

/**
 * Created by henrik on 2017-02-22.
 */
class SteamUserDAOImpl @Inject() ( /*config: Configuration,*/ steamProfileFactory: SteamProfileFactory) extends SteamUserDAO {
  import SteamUserDAOImpl._
  //private[this] lazy val key: String = config.getString("steam.key")
  val key = "CA06C916B80AE819D4C61E1C3A548666"
  private lazy val client = new SteamWebApiClient.SteamWebApiClientBuilder(key).build()

  import scala.concurrent.ExecutionContext.Implicits.global

  override def userSummaries(ids: List[SteamId]): Source[Seq[SteamProfile], NotUsed] = {
    Source.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val freshProfiles = ticker.mapAsync(5)(_ => TimestampedFuture[GetPlayerSummaries, List[SteamId]](ids) { l =>
        getUserSummaries(l)
      })
        .map {
          processSummaries(_)
        }
      val priorityMerge = builder.add(MergePreferred[Seq[SteamProfile]](1))
      val profileCache = builder.add(Flow[Seq[SteamProfile]].buffer(1, OverflowStrategy.dropHead))
      val broadcast = builder.add(Broadcast[Seq[SteamProfile]](2))
      //keeps a copy in cache and loops it back around. fresh copies replace old ones
      freshProfiles ~> priorityMerge.preferred
      priorityMerge <~ profileCache <~ broadcast.out(0)
      priorityMerge ~> broadcast
      SourceShape(broadcast.out(1))
    })
  }

  def getUserSummaries(ids: List[SteamId]) = {
    val req = SteamWebApiRequestFactory.createGetPlayerSummariesRequest(ids.asJava)
    client.processRequest[GetPlayerSummaries](req)
  }

  def processSummaries(summaries: GetPlayerSummaries): Seq[SteamProfile] = {
    summaries.getResponse.getPlayers.asScala.map {
      p: com.lukaspradel.steamapi.data.json.playersummaries.Player =>
        steamProfileFactory(p)
    }
  }

  def getOwnedGames(id: SteamId) = {
    val req = SteamWebApiRequestFactory.createGetOwnedGamesRequest(id, true, true, List[Integer]().asJava)
    client.processRequest[GetOwnedGames](req)
  }

  def processGames(games: GetOwnedGames) = {
    games.getResponse.getGames.asScala.map { g => (Game.fromApiModel(g), g.getPlaytimeForever.toInt) }
  }

  def getFriends(id: SteamId) = {
    val req = SteamWebApiRequestFactory.createGetFriendListRequest(id)
    client.processRequest[GetFriendList](req)
  }

  def processFriends(friends: GetFriendList) = {
    friends.getFriendslist.getFriends.asScala.map { f => (f.getSteamid, f.getFriendSince.toInt) }
  }

  override def userStatus(id: SteamId): String = ???

  override def currentGame(id: SteamId): Option[GameId] = ???
}

object SteamUserDAOImpl {
  private val ticker = Source.tick(initialDelay = 1.second, interval = 1.minute, Tick)
  case object Tick
}
