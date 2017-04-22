package controllers

import javax.inject.{ Inject, Named }

import akka.actor.ActorRef
import com.mohiva.play.silhouette.api.Silhouette
import models.Game.GameId
import models.{ Game, ServiceProfile }
import models.daos.SteamUserDAO.SteamId
import models.daos.{ GameDAO, ServiceProfileDAO, SteamUserDAO }
import models.services.ProfileGraphService
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.mvc.{ Action, Controller }
import play.twirl.api.Html
import utils.auth.DefaultEnv
import views.html.gamePage
import views.html.recommendations.{ friendsWithGameRec, similarPeopleWithGameRec }
import views.html.utils.concatenateHtml

import scala.concurrent.Future

/**
 * Created by Henrik on 2017-04-14.
 */
class RecommendationController @Inject() (steamUserDAO: SteamUserDAO, profileDAO: ServiceProfileDAO, neo: ProfileGraphService, gameDAO: GameDAO, @Named("updater") steamInfoUpdater: ActorRef, silhouette: Silhouette[DefaultEnv], val messagesApi: MessagesApi, implicit val webJarAssets: WebJarAssets) extends Controller with I18nSupport {
  import scala.concurrent.ExecutionContext.Implicits.global
  def recommendations(id: SteamId) = Action.async {
    val recent = neo.recentMutualPlayTime(id).flatMap { s: Seq[(SteamId, GameId)] =>
      val (people, games) = s.unzip
      val profilesFuture = profileDAO.bulkFind(people.toList)
      val gamesFuture = gameDAO.bulkFind(games.toList)
      profilesFuture.zip(gamesFuture).map { t: (List[ServiceProfile], List[Game]) =>
        val p: List[ServiceProfile] = t._1
        val g: List[Game] = t._2
        val lookup = s.toMap[SteamId, GameId]
        p.map { profile =>
          val gid = lookup(profile.id)
          val game = g.find(_.id == gid)
          (profile, game.get)
        }.toMap[ServiceProfile, Game]
      }
    }

    val total = neo.mutualTotalGameTime(id).flatMap { s: Seq[(SteamId, GameId)] =>
      val (people, games) = s.unzip
      val profilesFuture = profileDAO.bulkFind(people.toList)
      val gamesFuture = gameDAO.bulkFind(games.toList)
      profilesFuture.zip(gamesFuture).map { t: (List[ServiceProfile], List[Game]) =>
        val p: List[ServiceProfile] = t._1
        val g: List[Game] = t._2
        val lookup = s.toMap[SteamId, GameId]
        p.map { profile =>
          val gid = lookup(profile.id)
          val game = g.find(_.id == gid)
          (profile, game.get)
        }.toMap[ServiceProfile, Game]
      }
    } //.map((s.toMap[SteamId, GameId], _)) }
    val trending: Future[Iterable[(Game, Iterable[ServiceProfile])]] = neo.trendingGames(10).flatMap { gameIds =>
      val gamesFuture = gameDAO.bulkFind(gameIds.toList)
      gamesFuture.flatMap { games =>
        Future.sequence(games.map { game: Game =>
          val peopleFuture = neo.friendsWithGame(id, game.id)
          peopleFuture.flatMap { people =>
            profileDAO.bulkFind(people.toList).map { profiles: List[ServiceProfile] => (game, profiles) }
          }
        })
      }
    }
    for {
      r <- recent
      t1 <- total
      t2 <- trending
    } yield Ok(views.html.recommendations.allRecommendations(r, t1, t2))
  }

  def recByGame(gameId: GameId) = Action.async {
    val user = "76561198030588344"
    val gameFuture: Future[Option[Game]] = gameDAO.find(gameId)
    val friendsWithGameFuture: Future[Seq[ServiceProfile]] = neo.friendsWithGame(user, gameId).flatMap { friends => profileDAO.bulkFind(friends.toList) }
    val similarInterestsFuture: Future[Seq[ServiceProfile]] = neo.peopleLikeYou(user, 20).flatMap(people => neo.filterHasGame(people, gameId)).flatMap { people => profileDAO.bulkFind(people.toList) }
    for {
      gameOption <- gameFuture
      friends <- friendsWithGameFuture
      similar <- similarInterestsFuture
    } yield { println(similar); println(gameOption.get.toString); Ok(gamePage(gameOption.get, concatenateHtml(friendsWithGameRec(friends, gameOption.get.toString), similarPeopleWithGameRec(similar, gameOption.get.toString)))) }
  }
  /*private def mutualRecentlyPlayed(id: SteamId) = {
    neo.
  }
  private def mutualTotalTimePlayed(id: SteamId)
*/
}
