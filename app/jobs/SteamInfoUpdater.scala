package jobs

import javax.inject.Inject

import akka.actor._
import akka.http.scaladsl.model.DateTime
import com.mohiva.play.silhouette.api._
import jobs.SteamInfoUpdater._
import models.daos.{ GameDAO, GraphObjects, SteamProfileDAO, SteamUserDAO }
import models.daos.SteamUserDAO.SteamId
import models.services.ProfileGraphService
import models.{ Game, ServiceProfile, SteamProfile }
import play.modules.reactivemongo.MongoController

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Await, Future, blocking }
import scala.concurrent.duration._

/**
 * Created by henrik on 2017-03-08.
 */

class SteamInfoUpdater @Inject() (neo: ProfileGraphService, steamApi: SteamUserDAO, profileDAO: SteamProfileDAO, gameDAO: GameDAO) extends Actor with akka.actor.ActorLogging {

  override def receive: Receive = {
    case InitiateReload(users) => {
      println(s"starting reload of users: $users")
      self ! RefreshAttributesAndStatus(users)
      self ! RefreshGames(users)
      self ! RefreshFriends(users)
    }
    case RefreshAttributesAndStatus(users) => {
      Future {
        blocking {
          steamApi.processSummaries(steamApi.getUserSummaries(users))
        }
      }.onSuccess[Unit] {
        case profiles: Seq[SteamProfile] => {
          profileDAO.bufferedSave(profiles)
          profiles.foreach { p =>
            Future {
              blocking {
                println(p)
                neo.mergeProfile(p)
              }
            }
          }
        }
      }
    }
    case RefreshGames(users) => {
      users.foreach { user =>
        Future {
          blocking {
            steamApi.processGames(steamApi.getOwnedGames(user))
          }
        }.onSuccess[Unit] {
          case games: Seq[(Game, Int, Int)] => {
            games.grouped(GraphObjects.CYPHER_MAX).foreach(gameList =>
              Future {
                blocking {
                  if (user == "76561198030588344") { println("dfffffffffffffff          sssssssssss lll!!!!!!!"); gameList.foreach(println) }
                  neo.updateGames(user, gameList)
                }
              }.onFailure { case e: Exception => log.error(e, e.getMessage) }
            )
            gameDAO.bufferedSave(games.map(tuple => tuple._1), maxWait = 5.minutes)
          }
        }
      }
    }
    case RefreshFriends(users) => {
      users.foreach { user =>
        {
          Future {
            blocking {
              steamApi.processFriends(steamApi.getFriends(user))
            }
          }.onSuccess[Unit] {
            case friendTuples: Seq[(SteamId, Int)] => {
              friendTuples.grouped(GraphObjects.CYPHER_MAX).foreach(friendList =>
                Future {
                  blocking {
                    neo.updateFriends(user, friendList)
                  }
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
            blocking {
              steamApi.processFriends(steamApi.getFriends(user))
            }
          }.onSuccess[Unit] {
            case friendTuples: Seq[(SteamId, Int)] => {
              friendTuples.grouped(GraphObjects.CYPHER_MAX).foreach(friendList =>
                Future {
                  blocking {
                    println("!" + neo.updateFriends(user, friendList))
                  }
                }.onFailure { case e: Exception => log.error(e, e.getMessage) }
              )
            }
          }
        }
      }
    }
  }
  def userList: List[SteamId] = Await.result[List[String]](profileDAO.findAllUsers, 5.seconds)
  //val userList: List[SteamId] = List("76561198030588344", "76561198013223031", "76561197998468755", "76561198200246905", "76561198050782985", "76561198098609179", "76561197996581718") //read from db or similar in future
  //self ! InitiateReload(userList)
  val cancellable =
    context.system.scheduler.schedule(
      0.milliseconds,
      2.minutes,
      self,
      InitiateReload(userList))

  val listener = context.system.actorOf(Props(new Actor {
    def receive = {
      case e @ LoginEvent(identity, request) => println(request)
      case e @ LogoutEvent(identity, request) => println(e)
      case e @ SignUpEvent(identity, request) => println(e)
      case e @ NotAuthenticatedEvent(request) => println(e)
      case e @ AuthenticatedEvent(identity, request) => println(e)
    }
  }))
  val eventBus = EventBus()
  eventBus.subscribe(listener, classOf[LoginEvent[ServiceProfile]])
  eventBus.subscribe(listener, classOf[LogoutEvent[ServiceProfile]])
  eventBus.subscribe(listener, classOf[SignUpEvent[ServiceProfile]])
  eventBus.subscribe(listener, classOf[NotAuthenticatedEvent])
  eventBus.subscribe(listener, classOf[AuthenticatedEvent[ServiceProfile]])
}

object SteamInfoUpdater {

  case class InitiateReload(users: List[SteamId])

  case class RefreshAttributesAndStatus(users: List[SteamId])

  case class RefreshGames(users: Seq[SteamId])

  case class RefreshFriends(users: Seq[SteamId])

  case class RefreshFriendsOfFriends(friends: Seq[SteamId])

}
