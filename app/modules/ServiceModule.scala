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
import models.daos.{ SteamUserDAO, SteamUserDAOImpl }
import models._
import models.daos.ServiceUserDAO.ServiceProfileSource
import models.daos.SteamUserDAO.SteamId
import net.codingwell.scalaguice.ScalaModule
import play.api.Configuration
import play.api.libs.openid.OpenIdClient
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

/**
 * Created by henrik on 2017-02-26.
 */
class ServiceModule extends AbstractModule with ScalaModule {

  /**
   * Configures the module.
   */
  def configure(): Unit = {
    bind[SteamUserDAO].to[SteamUserDAOImpl]
    bind[SteamProfileFactory].to[SteamProfileFactoryImpl]
  }

  @Provides
  def allUserSummariesProvider(@Inject steamUserSummaries: Source[Seq[SteamProfile], NotUsed]): ServiceProfileSource = {
    steamUserSummaries.buffer(1, OverflowStrategy.dropTail) //if several services in future, merge streams here
  }

  @Provides
  def steamSummariesProvider(@Inject steamUserDAO: SteamUserDAO, @Inject ids: List[SteamId], @Inject steamProfileFactory: SteamProfileFactory) =
    steamUserDAO.userSummaries(ids, steamProfileFactory)

  @Provides
  def provideSteamProvider(
    cacheLayer: CacheLayer,
    httpLayer: HTTPLayer,
    client: OpenIdClient,
    configuration: Configuration): SteamProvider = {

    val settings = configuration.underlying.as[OpenIDSettings]("silhouette.steam")
    new SteamProvider(httpLayer, new PlayOpenIDService(client, settings), settings)
  }

  @Provides
  def steamIds: List[SteamId] = List("76561198030588344", "76561198030588344", "76561197998468755") //read from db or similar in future
}

