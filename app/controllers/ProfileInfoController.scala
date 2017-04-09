package controllers

import java.util.UUID
import javax.inject.{ Inject, Named }

import akka.actor.ActorRef
import com.mohiva.play.silhouette.api.Silhouette
import jobs.SteamInfoUpdater
import jobs.SteamInfoUpdater.InitiateReload
import models.Game.GameId
import models.{ Game, ServiceProfile }
import models.daos.{ GameDAO, ServiceProfileDAO, SteamUserDAO }
import models.daos.SteamUserDAO.SteamId
import models.services.{ ProfileGraphService, UserService }
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.mvc.{ Action, Controller }
import reactivemongo.api.commands.WriteError
import utils.auth.DefaultEnv

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.Try

/**
 * Created by henrik on 2017-03-29.
 */
class ProfileInfoController @Inject() (steamUserDAO: SteamUserDAO, profileDAO: ServiceProfileDAO, neo: ProfileGraphService, gameDAO: GameDAO, @Named("updater") steamInfoUpdater: ActorRef, silhouette: Silhouette[DefaultEnv], val messagesApi: MessagesApi, implicit val webJarAssets: WebJarAssets) extends Controller with I18nSupport {
  //val userList: List[SteamId] = List("76561198030588344", "76561198013223031", "76561197998468755", "76561198200246905", "76561198050782985", "76561198098609179", "76561197996581718") //read from db or similar in future
  import scala.concurrent.ExecutionContext.Implicits.global

  val users = Await.result[mutable.Buffer[String]](profileDAO.findAllUserIds.map(_.toBuffer), 5.seconds)

  def addProfile(id: String) = Action.async { implicit request =>
    println("Adding!")
    steamInfoUpdater ! InitiateReload(List(id))
    users.append(id)
    val profile = Future {
      steamUserDAO.processSummaries(steamUserDAO.getUserSummaries(List(id)))
    }
    profile.flatMap(ps => profileDAO.save(ps.head).map { l => Ok(l.foldLeft("")((t: String, s: WriteError) => s"$t ${s.toString}")) })
  }

  def readProfile(id: String) = Action.async { implicit request =>
    println("Reading!")
    println(users)
    println(users.size)
    val friends: Try[Future[List[ServiceProfile]]] = neo.getFriends(id).map { ids => profileDAO.bulkFind(ids.map { _._1 }.toList) }
    val games: Try[Future[List[Game]]] = neo.getGames(id).map { ids => gameDAO.bulkFind(ids.map { _._1 }.toList) }
    friends.foreach(_.onComplete(_.foreach(println)))
    games.foreach(_.onComplete(_.foreach(println)))
    friends.map { friendsFuture =>
      games.map { gamesFuture =>
        for {
          fs <- friendsFuture
          gs <- gamesFuture
          profileOption <- profileDAO.find(id)
        } yield profileOption.map { profile => Ok(views.html profilePage (profile, fs, gs)) }.get
        //OrElse(NotFound(views.html.errors.notFoundPage()))
      }
    }.flatten.get
  }
}
