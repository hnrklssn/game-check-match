package models.daos

import javax.inject.Inject

import akka.stream.scaladsl._
import com.lukaspradel.steamapi.data.json.friendslist.GetFriendList
import com.lukaspradel.steamapi.data.json.ownedgames.GetOwnedGames
import com.lukaspradel.steamapi.data.json.playersummaries.GetPlayerSummaries
import com.lukaspradel.steamapi.webapi.client.SteamWebApiClient
import com.lukaspradel.steamapi.webapi.request.builders.SteamWebApiRequestFactory
import models.Game.GameId
import models._
import models.daos.SteamUserDAO.SteamId
import play.Configuration

import scala.collection.JavaConverters._
import scala.concurrent.duration._

/**
 * Created by henrik on 2017-02-22.
 */
class SteamUserDAOImpl @Inject() (config: Configuration, steamProfileFactory: SteamProfileFactory) extends SteamUserDAO {
  private lazy val key: String = config.getString("steam.key")
  private lazy val client = new SteamWebApiClient.SteamWebApiClientBuilder(key).build()

  //  override def userSummaries(ids: List[SteamId]): Source[Seq[SteamProfile], NotUsed] = {
  //    Source.fromGraph(GraphDSL.create() { implicit builder =>
  //      import GraphDSL.Implicits._
  //
  //      val freshProfiles = ticker.mapAsync(5)(_ => TimestampedFuture[GetPlayerSummaries, List[SteamId]](ids) { l =>
  //        getUserSummaries(l)
  //      })
  //        .map {
  //          processSummaries(_)
  //        }
  //      val priorityMerge = builder.add(MergePreferred[Seq[SteamProfile]](1))
  //      val profileCache = builder.add(Flow[Seq[SteamProfile]].buffer(1, OverflowStrategy.dropHead))
  //      val broadcast = builder.add(Broadcast[Seq[SteamProfile]](2))
  //      //keeps a copy in cache and loops it back around. fresh copies replace old ones
  //      freshProfiles ~> priorityMerge.preferred
  //      priorityMerge <~ profileCache <~ broadcast.out(0)
  //      priorityMerge ~> broadcast
  //      SourceShape(broadcast.out(1))
  //    })
  //  }

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
    games.getResponse.getGames.asScala.map { g => (Game.fromApiModel(g), g.getPlaytimeForever.toInt, g.getAdditionalProperties.getOrDefault("playtime_2weeks", "0").toString.toInt) }
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

  /*val buffer = mutable.ParSet[SteamId]()
  var waitStartTime: Option[LocalDateTime] = None
  override def bufferFetchProfiles(ids: Iterable[SteamId]): Future[Seq[SteamProfile]] = {
    if (buffer.size == 0) {
      waitStartTime = Some(LocalDateTime.now())
    }
    buffer ++ ids
    Future {
      Seq(SteamProfileImpl("sdf", true, "sadf", "sdf", Online, NoGame))
    }
  }
  maybe turn into actor in the future...*/
}

object SteamUserDAOImpl {
  private val ticker = Source.tick(initialDelay = 1.second, interval = 1.minute, Tick)
  case object Tick
}
