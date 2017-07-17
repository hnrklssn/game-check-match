package modules

import javax.inject.Inject

import com.google.inject.{ AbstractModule, Provides }
import jobs.SteamInfoUpdater
import models._
import models.daos._
import models.services.{ ProfileGraphService, UserMicroService, UserMicroServiceRegistry }
import net.ceedubs.ficus.Ficus._
import net.codingwell.scalaguice.ScalaModule
import org.neo4j.driver.v1.{ AuthTokens, Driver, GraphDatabase }
import play.api.Configuration
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
    bind[GameDAO].to[GameDAOImpl]
  }

  //  @Provides
  //  def allUserSummariesProvider(@Inject steamUserSummaries: Source[Seq[SteamProfile], NotUsed]): ServiceProfileSource = {
  //    steamUserSummaries.buffer(1, OverflowStrategy.dropTail).take(1) //if several services in future, merge streams here
  //  }

  //  @Provides
  //  def steamSummariesProvider(@Inject steamUserDAO: SteamUserDAO, @Inject ids: List[SteamId], @Inject steamProfileFactory: SteamProfileFactory) =
  //    steamUserDAO.userSummaries(ids)

  @Provides
  def neoDriver(configuration: Configuration): Driver = {
    val url = configuration.underlying.as[String]("neo4j.url")
    val username = configuration.underlying.as[String]("neo4j.username")
    val password = configuration.underlying.as[String]("neo4j.password")
    GraphDatabase.driver(url, AuthTokens.basic(username, password))
  }

  @Provides
  def neoDAO(neo: Neo4jDAOImpl): ProfileGraphService = {
    neo.constrain()
    neo
  }

  @Provides
  def microUserServices(@Inject steamUserMicroServiceImpl: UserMicroService[SteamProfile]): UserMicroServiceRegistry = UserMicroServiceRegistry(Seq(steamUserMicroServiceImpl))

  //@Provides
  //def microUserServices(@Inject steamUserMicroServiceImpl: UserMicroService[SteamProfile]): Seq[UserMicroService[_]] = Seq(steamUserMicroServiceImpl)
}

