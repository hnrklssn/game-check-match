package modules

import javax.inject.Inject

import akka.NotUsed
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Source
import com.google.inject.{ AbstractModule, Provides }
import com.mohiva.play.silhouette.api.util.{ CacheLayer, HTTPLayer }
import com.mohiva.play.silhouette.impl.providers.OpenIDSettings
import com.mohiva.play.silhouette.impl.providers.openid.SteamProvider
import com.mohiva.play.silhouette.impl.providers.openid.services.PlayOpenIDService
import jobs.SteamInfoUpdater
import models.daos._
import models._
import models.daos.ServiceUserDAO.ServiceProfileSource
import models.daos.SteamUserDAO.SteamId
import models.services.{ ProfileGraphService, SteamUserMicroServiceImpl, UserMicroServiceRegistry }
import net.codingwell.scalaguice.ScalaModule
import play.api.Configuration
import play.api.libs.openid.OpenIdClient
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import org.neo4j.driver.v1.{ AccessMode, AuthTokens, Driver, GraphDatabase }
import play.api.libs.concurrent.AkkaGuiceSupport

/**
 * Created by henrik on 2017-02-26.
 */
class ServiceModule extends AbstractModule with ScalaModule with AkkaGuiceSupport {

  /**
   * Configures the module.
   */
  def configure(): Unit = {
    bind[SteamUserDAO].to[SteamUserDAOImpl]
    bind[SteamProfileFactory].to[SteamProfileFactoryImpl]
    bindActor[SteamInfoUpdater]("updater")
    bind[ProfileGraphService].to[Neo4jDAOImpl]
    bind[GameDAO].to[GameDAOImpl]
  }

  @Provides
  def allUserSummariesProvider(@Inject steamUserSummaries: Source[Seq[SteamProfile], NotUsed]): ServiceProfileSource = {
    steamUserSummaries.buffer(1, OverflowStrategy.dropTail).take(1) //if several services in future, merge streams here
  }

  @Provides
  def steamSummariesProvider(@Inject steamUserDAO: SteamUserDAO, @Inject ids: List[SteamId], @Inject steamProfileFactory: SteamProfileFactory) =
    steamUserDAO.userSummaries(ids)

  @Provides
  def steamIds: List[SteamId] = List("76561198030588344", "76561198013223031", "76561197998468755", "76561198200246905", "76561198050782985", "76561198098609179", "76561197996581718") //read from db or similar in future

  @Provides
  def neoDriver: Driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "test"))

  @Provides
  def microUserServices(@Inject steamUserMicroServiceImpl: SteamUserMicroServiceImpl): UserMicroServiceRegistry = UserMicroServiceRegistry(Seq(steamUserMicroServiceImpl))
}

