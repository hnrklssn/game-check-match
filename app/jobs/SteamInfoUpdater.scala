package jobs

import javax.inject.Inject

import akka.actor._
import jobs.SteamInfoUpdater._
import models.daos.{ GameDAO, GraphObjects, ServiceProfileDAO, SteamUserDAO }
import models.daos.SteamUserDAO.SteamId
import models.services.ProfileGraphService
import models.{ Game, SteamProfile }
import play.modules.reactivemongo.MongoController

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

/**
 * Created by henrik on 2017-03-08.
 */

class SteamInfoUpdater @Inject() (neo: ProfileGraphService, steamApi: SteamUserDAO, profileDAO: ServiceProfileDAO, gameDAO: GameDAO) extends Actor with akka.actor.ActorLogging {

  override def receive: Receive = {
    case InitiateReload(users) => {
      println(s"starting reload of users: $users")
      self ! RefreshAttributesAndStatus(users)
      self ! RefreshGames(users)
      self ! RefreshFriends(users)
    }
    case RefreshAttributesAndStatus(users) => {
      Future {
        steamApi.processSummaries(steamApi.getUserSummaries(users))
      }.onSuccess[Unit] {
        case profiles: Seq[SteamProfile] => profiles.foreach {
          profileDAO.save(_)
          neo.mergeProfile(_)
        }
      }
    }
    case RefreshGames(users) => {
      users.foreach { user =>
        Future {
          steamApi.processGames(steamApi.getOwnedGames(user))
        }.onSuccess[Unit] {
          case games: Seq[(Game, Int)] => {
            games.grouped(GraphObjects.CYPHER_MAX).foreach(gameList =>
              Future {
                neo.updateGames(user, gameList)
              }.onFailure { case e: Exception => log.error(e, e.getMessage) }
            )
            games.foreach(tuple => gameDAO.upsert(tuple._1))
          }
        }
      }
    }
    case RefreshFriends(users) => {
      users.foreach { user =>
        {
          Future {
            steamApi.processFriends(steamApi.getFriends(user))
          }.onSuccess[Unit] {
            case friendTuples: Seq[(SteamId, Int)] => {
              friendTuples.grouped(GraphObjects.CYPHER_MAX).foreach(friendList =>
                Future {
                  neo.updateFriends(user, friendList)
                }.onFailure { case e: Exception => log.error(e, e.getMessage) }
              )
              val friendIds = friendTuples.map(_._1)
              self ! RefreshAttributesAndStatus(friendIds.toList)
              self ! RefreshGames(friendIds)
              self ! RefreshFriendsOfFriends(friendIds)
            }
          }
        }
      }
    }
    case RefreshFriendsOfFriends(users) => {
      users.foreach { user =>
        {
          Future {
            steamApi.processFriends(steamApi.getFriends(user))
          }.onSuccess[Unit] {
            case friendTuples: Seq[(SteamId, Int)] => {
              friendTuples.grouped(GraphObjects.CYPHER_MAX).foreach(friendList =>
                Future {
                  println("!" + neo.updateFriends(user, friendList))
                }.onFailure { case e: Exception => log.error(e, e.getMessage) }
              )
            }
          }
        }
      }
    }
  }
  def userList: List[SteamId] = Await.result[List[String]](profileDAO.findAllUserIds, 5.seconds)
  //self ! InitiateReload(userList)
  val cancellable =
    context.system.scheduler.schedule(
      0.milliseconds,
      50.minutes,
      self,
      InitiateReload(userList))
}

object SteamInfoUpdater {

  case class InitiateReload(users: List[SteamId])

  case class RefreshAttributesAndStatus(users: List[SteamId])

  case class RefreshGames(users: Seq[SteamId])

  case class RefreshFriends(users: Seq[SteamId])

  case class RefreshFriendsOfFriends(friends: Seq[SteamId])

}
