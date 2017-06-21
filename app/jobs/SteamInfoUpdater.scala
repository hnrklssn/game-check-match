package jobs

import java.util
import java.util.concurrent.PriorityBlockingQueue
import javax.inject.Inject

import akka.actor._
import akka.http.scaladsl.model.DateTime
import com.mohiva.play.silhouette.api._
import jobs.SteamInfoUpdater._
import models.daos.{GameDAO, GraphObjects, SteamProfileDAO, SteamUserDAO}
import models.daos.SteamUserDAO.SteamId
import models.services.ProfileGraphService
import models.{Game, ServiceProfile, SteamProfile}
import play.modules.reactivemongo.MongoController

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future, blocking}
import scala.concurrent.duration._

/**
  * Created by henrik on 2017-03-08.
  */

class SteamInfoUpdater @Inject()(neo: ProfileGraphService, steamApi: SteamUserDAO, profileDAO: SteamProfileDAO, gameDAO: GameDAO) extends Actor with akka.actor.ActorLogging {
  override def receive: Receive = {
    case InitiateReload(users, prio) => {
      println(s"starting reload of users: $users")
      self ! RefreshAttributesAndStatus(users, prio)
      self ! RefreshGames(users, prio)
      self ! RefreshFriends(users, prio)
    }
    case RefreshAttributesAndStatus(users, prio) => {
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
    case RefreshGames(users, prio) => {
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
                  neo.updateGames(user, gameList)
                }
              }.onFailure { case e: Exception => log.error(e, e.getMessage) }
            )
            gameDAO.bufferedSave(games.map(tuple => tuple._1), maxWait = 5.minutes)
          }
        }
      }
    }
    case RefreshFriends(users, prio) => {
      users.foreach { user => {
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
            self ! RefreshAttributesAndStatus(friendIds.toList, prio)
            self ! RefreshGames(friendIds, prio - 1)
            self ! RefreshFriendsOfFriends(friendIds, prio - 5)
          }
        }
      }
      }
    }
    case RefreshFriendsOfFriends(users, prio) => {
      users.foreach { user => {
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
    case SignUpEvent(identity, request) =>
      identity match {
        case s: ServiceProfile => self ! InitiateReload(List(s.id), Int.MaxValue)
          userList = s.id :: userList
      }
    case RefreshUserList(prio) =>
      val future = profileDAO.findAllUsers
      future.onSuccess[Unit]{case l: List[SteamId] => userList = l}
      future.onFailure[Unit]{case t: Throwable => System.err.println(t.getMessage)
        System.err.println("Is the profile db connected? Retrying...")
        self ! RefreshUserList(prio)
      }
  }

  @volatile
  private var userList: List[SteamId] = Nil


  self ! RefreshUserList(Int.MaxValue)
  self ! InitiateReload(userList)
  private val cancellable =
      context.system.scheduler.schedule(
        5.minutes,
        20.minutes,
        self,
        InitiateReload(userList))
  private val listener = context.system.actorOf(Props(new Actor {
    def receive = {
      case e@LoginEvent(identity, request) => println(request)
      case e@LogoutEvent(identity, request) => println(e)
      case e@SignUpEvent(identity, request) => println(e)
      case e@NotAuthenticatedEvent(request) => println(e)
      case e@AuthenticatedEvent(identity, request) => println(e)
    }
  }))

  private val eventBus = EventBus()
  eventBus.subscribe(listener, classOf[LoginEvent[ServiceProfile]])
  eventBus.subscribe(listener, classOf[LogoutEvent[ServiceProfile]])
  eventBus.subscribe(listener, classOf[SignUpEvent[ServiceProfile]])
  eventBus.subscribe(listener, classOf[NotAuthenticatedEvent])
  eventBus.subscribe(listener, classOf[AuthenticatedEvent[ServiceProfile]])

  eventBus.subscribe(self, classOf[SignUpEvent[ServiceProfile]])
}

object SteamInfoUpdater {
  private val DEFAULT_PRIO: Int = 1

  trait InfoUpdaterMessage {
    def priority: Int
  }

  case class InitiateReload(users: List[SteamId], priority: Int = DEFAULT_PRIO) extends InfoUpdaterMessage

  case class RefreshAttributesAndStatus(users: List[SteamId], priority: Int = DEFAULT_PRIO) extends InfoUpdaterMessage

  case class RefreshGames(users: Seq[SteamId], priority: Int = DEFAULT_PRIO) extends InfoUpdaterMessage

  case class RefreshFriends(users: Seq[SteamId], priority: Int = DEFAULT_PRIO) extends InfoUpdaterMessage

  case class RefreshFriendsOfFriends(friends: Seq[SteamId], priority: Int = DEFAULT_PRIO) extends InfoUpdaterMessage

  case class RefreshUserList(priority: Int) extends InfoUpdaterMessage

  import akka.actor.ActorRef
  import akka.actor.ActorSystem
  import akka.dispatch.Envelope
  import akka.dispatch.MailboxType
  import akka.dispatch.MessageQueue
  import akka.dispatch.ProducesMessageQueue
  import com.typesafe.config.Config

  import java.util.concurrent.PriorityBlockingQueue
  import com.google.common.collect.ForwardingQueue

  // Marker trait used for mailbox requirements mapping
  trait SteamInfoUpdaterQueue

  // This is the MessageQueue implementation
  class UniquePriorityMessageQueue extends MessageQueue
    with SteamInfoUpdaterQueue {

    private final val queue = new UniquePriorityBlockingQueue[Envelope](500, Ordering.by(e => e.message match {
      case m: InfoUpdaterMessage => m.priority
      case _ => Int.MaxValue
    }))

    // these should be implemented; queue used as example
    def enqueue(receiver: ActorRef, handle: Envelope): Unit =
      queue.offer(handle)

    def dequeue(): Envelope = queue.poll()

    def numberOfMessages: Int = queue.size

    def hasMessages: Boolean = !queue.isEmpty

    def cleanUp(owner: ActorRef, deadLetters: MessageQueue) {
      while (hasMessages) {
        deadLetters.enqueue(owner, dequeue())
      }
    }
  }

  class UniquePriorityBlockingQueue[T](initialCapacity: Int, ordering: Ordering[T]) extends ForwardingQueue[T] {
    override def delegate(): util.Queue[T] = new PriorityBlockingQueue[T](initialCapacity, ordering)

    override def add(element: T): Boolean = if (super.contains(element)) {
      false
    } else {
      super.add(element)
    }

    override def offer(o: T): Boolean = standardOffer(o)
  }

  // This is the Mailbox implementation
  class MyUnboundedMailbox extends MailboxType
    with ProducesMessageQueue[UniquePriorityMessageQueue] {


    // This constructor signature must exist, it will be called by Akka
    def this(settings: ActorSystem.Settings, config: Config) = {
      // put your initialization code here
      this()
    }

    // The create method is called to create the MessageQueue
    final override def create(
                               owner: Option[ActorRef],
                               system: Option[ActorSystem]): MessageQueue =
      new UniquePriorityMessageQueue()
  }

}
