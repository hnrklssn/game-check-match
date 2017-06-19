package controllers

import java.util.UUID
import javax.inject.{ Inject, Named }

import akka.actor.ActorRef
import com.mohiva.play.silhouette.api.exceptions.ProviderException
import com.mohiva.play.silhouette.api.{ LoginEvent, Silhouette }
import com.mohiva.play.silhouette.impl.providers.{ SocialProvider, SocialProviderRegistry }
import jobs.SteamInfoUpdater
import jobs.SteamInfoUpdater.InitiateReload
import models.Game.GameId
import models.{ Game, MutualFriendsRecommendation, Recommendation, ServiceProfile }
import models.daos.{ GameDAO, SteamProfileDAO, SteamUserDAO }
import models.daos.SteamUserDAO.SteamId
import models.services.{ ProfileGraphService, UserMicroService }
import play.api.i18n.{ I18nSupport, Messages, MessagesApi }
import play.api.mvc.{ Action, Controller }
import play.twirl.api.Html
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
class ProfileInfoController @Inject() (socialProviderRegistry: SocialProviderRegistry, steamUserDAO: SteamUserDAO, profileDAO: SteamProfileDAO, neo: ProfileGraphService, gameDAO: GameDAO, @Named("updater") steamInfoUpdater: ActorRef, silhouette: Silhouette[DefaultEnv], val messagesApi: MessagesApi, implicit val webJarAssets: WebJarAssets) extends Controller with I18nSupport {
  import scala.concurrent.ExecutionContext.Implicits.global

  val users = Await.result[mutable.Buffer[String]](profileDAO.findAllUsers.map(_.toBuffer), 5.seconds)

  def addProfile(id: String) = Action.async { implicit request =>
    println("Adding!")
    steamInfoUpdater ! InitiateReload(List(id))
    users.append(id)
    val profile = Future {
      steamUserDAO.processSummaries(steamUserDAO.getUserSummaries(List(id)))
    }
    profile.flatMap(ps => profileDAO.save(ps.head.register).map { l => Ok(l.displayName) })
  }

  def readProfile(id: String) = silhouette.UserAwareAction.async { implicit request =>
    println("Reading!")
    println(users)
    println(users.size)
    val friends: Future[List[ServiceProfile]] = neo.getFriends(id).flatMap { ids: Seq[(SteamId, Int)] =>
      profileDAO.bulkFind(ids.map { _._1 }.toList)
    }
    val games: Future[List[Game]] = neo.getGames(id).flatMap { ids =>
      gameDAO.bulkFind(ids.map { _._1 }.toList)
    }
    val mutualFriendsFuture: Future[Option[List[ServiceProfile]]] = request.identity.map { user =>
      neo.mutualFriends(Seq(user.id, id))
        .flatMap(ids => profileDAO.bulkFind(ids.toList))
    } match {
      case Some(f) => f.map(Some(_))
      case None => Future.successful(None)
    }

    friends.onComplete(_.foreach(println))
    games.onComplete(_.foreach(println))
    for {
      fs <- friends
      gs <- games
      mutualFriendsOption <- mutualFriendsFuture
      profileOption <- profileDAO.find(id)
    } yield {
      val mutualFriendsRecOpt: Option[Html] = for {
        mutualFriends <- mutualFriendsOption
        user <- request.identity
      } yield { println(mutualFriends); views.html.recommendations.mutualFriendsRec(mutualFriends, user.displayName) }
      Ok(views.html.profilePage(profileOption.get, fs, gs, mutualFriendsRecOpt.get))
    }
    // Ok(mutualFriendsOr.
    //}.get
    //OrElse(NotFound(views.html.errors.notFoundPage()))
  }

  def readProfileAuto() = silhouette.SecuredAction { implicit request =>
    Redirect(routes.ProfileInfoController.readProfile(request.identity.id))
  }
}
