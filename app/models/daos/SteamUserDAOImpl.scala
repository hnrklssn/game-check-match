package models.daos

import java.time.LocalDateTime

import com.lukaspradel.steamapi.webapi.client.SteamWebApiClient
import com.lukaspradel.steamapi.webapi.request.builders.SteamWebApiRequestFactory
import com.lukaspradel.steamapi.data.json.playersummaries.GetPlayerSummaries
import play.Configuration
import javax.inject.Inject

import scala.concurrent.duration._
import collection.JavaConverters._
import models.daos.SteamUserDAO.SteamId
import akka.stream.scaladsl._
import models.{ ServiceProfile, SteamProfile, SteamProfileFactory }
import ServiceProfile._
import akka.NotUsed
import akka.stream.{ OverflowStrategy, SourceShape }
import models.Game.GameId
import utils.TimestampedFuture

import scala.concurrent.Future

/**
 * Created by henrik on 2017-02-22.
 */
class SteamUserDAOImpl @Inject() (config: Configuration) extends SteamUserDAO {
  import SteamUserDAOImpl._
  private[this] lazy val key: String = config.getString("steam.key")
  private lazy val client = new SteamWebApiClient.SteamWebApiClientBuilder(key).build()

  import scala.concurrent.ExecutionContext.Implicits.global

  override def userSummaries(ids: List[SteamId], steamProfileFactory: SteamProfileFactory): Source[Seq[SteamProfile], NotUsed] = {
    Source.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val freshProfiles = ticker.mapAsync(5)(_ => TimestampedFuture[GetPlayerSummaries, List[SteamId]](ids) { l =>
        getUserSummaries(l)
      })
        .map {
          processSummaries(_, steamProfileFactory)
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

  private def getUserSummaries(ids: List[SteamId]) = {
    val req = SteamWebApiRequestFactory.createGetPlayerSummariesRequest(ids.asJava)
    client.processRequest[GetPlayerSummaries](req)
  }

  private def processSummaries(summaries: GetPlayerSummaries, steamProfileFactory: SteamProfileFactory): Seq[SteamProfile] = {
    summaries.getResponse.getPlayers.asScala.map {
      p: com.lukaspradel.steamapi.data.json.playersummaries.Player =>
        steamProfileFactory(p)
    }
  }

  override def userStatus(id: SteamId): String = ???

  override def currentGame(id: SteamId): Option[GameId] = ???
}

object SteamUserDAOImpl {
  private val ticker = Source.tick(initialDelay = 1.second, interval = 1.minute, Tick)
  case object Tick
}
