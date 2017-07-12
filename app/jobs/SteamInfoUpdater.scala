package jobs

import java.util
import java.util.concurrent.PriorityBlockingQueue
import javax.inject.Inject

import akka.actor._
import com.mohiva.play.silhouette.api._
import jobs.SteamInfoUpdater._
import models.daos.{GameDAO, GraphObjects, SteamProfileDAO, SteamUserDAO}
import models.daos.SteamUserDAO.SteamId
import models.services.ProfileGraphService
import models.{Game, ServiceProfile, SteamProfile}
import play.modules.reactivemongo.MongoController
import org.neo4j.driver.v1.exceptions.Neo4jException

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future, blocking}
import scala.concurrent.duration._
import akka.dispatch.RequiresMessageQueue
import org.joda.time.{DateTime, Hours, Minutes}

import scala.util.{Failure, Success, Try}

/**
 * Created by henrik on 2017-03-08.
 */

class SteamInfoUpdater @Inject() (neo: ProfileGraphService, steamApi: SteamUserDAO, profileDAO: SteamProfileDAO, gameDAO: GameDAO) extends Actor with akka.actor.ActorLogging with RequiresMessageQueue[MyPrioQueueSemantics] {

  private val lastUpdated = mutable.Map[AnyRef, DateTime]().par
  private def isStale(element: AnyRef, duration: Minutes = Minutes.minutes(15)): Boolean = Minutes.minutesBetween(lastUpdated.getOrElse(element, DateTime.now), DateTime.now()).isLessThan(duration)
  private def turnFresh(element: AnyRef): Unit = lastUpdated += element -> DateTime.now()

  override def receive: Receive = {
    case InitiateReload(users, prio) => {
      println(s"starting reload of users: $users")
      self ! RefreshAttributesAndStatus(users, prio)
      self ! RefreshGames(users, prio)
      self ! RefreshFriends(users, prio)
    }
    case RefreshAttributesAndStatus(users, prio) => {
      val stale = users.filter(isStale(_))
      Future {
        blocking {
          steamApi.processSummaries(steamApi.getUserSummaries(stale))
        }
      }.onSuccess[Unit] {
        case profiles: Seq[SteamProfile] => {
          profileDAO.bufferedSave(profiles)
          profiles.foreach { p =>
            val f = neo.mergeProfile(p)
            f onSuccess { case i => println(s"steaminfoupdater:56 $p $i"); turnFresh( p.id) }
            f onFailure { case e: Neo4jException => println(s"steaminfoupdater:57 $p ${e.code()}")
              self ! RefreshAttributesAndStatus(List(p.id), prio - 50)
            }
          }
        }
      }
    }
    case RefreshGames(users, prio) => {
      val stale = users.map(_ -> 'ownedGames).filter(isStale(_))
      stale.foreach { t =>
        val user = t._1
        Future {
          blocking {
            steamApi.processGames(steamApi.getOwnedGames(user))
          }
        }.onSuccess[Unit] {
          case games: Seq[(Game, Int, Int)] => {
            val futureTries: Seq[Future[Try[Boolean]]] = games.grouped(GraphObjects.CYPHER_MAX).toSeq
              .map{gameList => neo.updateGames(user, gameList)}
              .map( f => f.map(x => Success(x)).recover{case e: Throwable => Failure(e)})

            val fSeqTry: Future[Seq[Try[_]]] = Future.sequence(futureTries)
            fSeqTry.onSuccess{ case ts =>
              if (ts.forall(_.isSuccess)) {
                println(s"steaminfoupdater:79 $user")
                turnFresh(t)
              } else {
                val errors = ts.count(_.isFailure)
                println(s"steaminfoupdater:83 $errors errors")
                self ! RefreshGames(List(user), prio - 50 * errors)
              }
            }
            fSeqTry.onFailure { case e: Exception => log.error(e, e.getMessage)
              println(s"steaminfoupdater:88 $user $e")
              self ! RefreshGames(List(user), prio - 1000)
            }
            val staleGames = games.map(tuple => tuple._1).filter(isStale(_, Minutes.minutes(120)))
            val g = gameDAO.bufferedSave(staleGames, maxWait = 5.minutes)
            g.onSuccess{ case _ => staleGames.foreach(turnFresh)}
          }
        }
      }
    }
    case RefreshFriends(users, prio) => {
      val stale = users.map(_ -> 'friends).filter(isStale(_))
      println(s"steaminfoupdater:100 $stale")
      stale.foreach { t =>
        val user = t._1
        Future {
          blocking {
            steamApi.processFriends(steamApi.getFriends(user))
          }
        }.onSuccess[Unit] {
          case friendTuples: Seq[(SteamId, Int)] => {
            val futureTries: Seq[Future[Try[_]]] = friendTuples.grouped(GraphObjects.CYPHER_MAX).toSeq
              .map { friendList => neo.updateFriends(user, friendList)}
              .map( f => f.map(x => Success(x)).recover{case e: Throwable => Failure(e)})

            val fSeqTry: Future[Seq[Try[_]]] = Future.sequence(futureTries)
            fSeqTry.onSuccess{ case ts =>
              if (ts.forall(_.isSuccess)) {
                println(s"steaminfoupdater:116 $user")
                turnFresh(t)
                val friendIds = friendTuples.map(_._1)
                self ! RefreshAttributesAndStatus(friendIds.toList, prio)
                self ! RefreshGames(friendIds, prio - 1)
                self ! RefreshFriendsOfFriends(friendIds, prio - 5)
              } else {
                val errors = ts.count(_.isFailure)
                println(s"steaminfoupdater:120 $errors errors")
                self ! RefreshGames(List(user), prio - 50 * errors)
              }
            }
            fSeqTry.onFailure { case e: Exception => log.error(e, e.getMessage)
              println(s"steaminfoupdater:125 $user $e")
              self ! RefreshGames(List(user), prio - 1000)
            }
          }
        }
      }
    }
    case RefreshFriendsOfFriends(users, prio) => {
      val stale = users.map(_ -> 'friends).filter(isStale(_))
      stale.foreach { t =>
        val user = t._1
        Future {
          blocking {
            steamApi.processFriends(steamApi.getFriends(user))
          }
        }.onSuccess[Unit] {
          case friendTuples: Seq[(SteamId, Int)] => {
            val futureTries: Seq[Future[Try[_]]] = friendTuples.grouped(GraphObjects.CYPHER_MAX).toSeq
              .map { friendList => neo.updateFriends(user, friendList)}
              .map( f => f.map(x => Success(x)).recover{case e: Throwable => Failure(e)})

            val fSeqTry: Future[Seq[Try[_]]] = Future.sequence(futureTries)
            fSeqTry.onSuccess{ case ts =>
              if (ts.forall(_.isSuccess)) {
                println(s"steaminfoupdater:153 $user")
                turnFresh(t)
              } else {
                val errors = ts.count(_.isFailure)
                println(s"steaminfoupdater:157 $errors errors")
                self ! RefreshGames(List(user), prio - 100 * errors)
              }
            }
            fSeqTry.onFailure { case e: Exception => log.error(e, e.getMessage)
              println(s"steaminfoupdater:162 $user $e")
              self ! RefreshGames(List(user), prio - 10000)
            }
          }
        }
      }
    }
    case SignUpEvent(identity, request) =>
      println("signup!")
      identity match {
        case s: ServiceProfile =>
          println("initiating reload!")
          self ! InitiateReload(List(s.id), Int.MaxValue)
          userList = s.id :: userList
      }
    case RefreshUserList(prio) =>
      val future = profileDAO.findAllUsers
      future.onSuccess[Unit] { case l: List[SteamId] => userList = l }
      future.onFailure[Unit] {
        case t: Throwable =>
          System.err.println(t.getMessage)
          System.err.println("Is the profile db connected? Retrying...")
          self ! RefreshUserList(prio)
      }
  }

  @volatile
  private var userList: List[SteamId] = Nil

  self ! RefreshUserList(Int.MaxValue)
  self ! InitiateReload(userList)
  /*private val cancellable =
    context.system.scheduler.schedule(
      5.minutes,
      20.minutes,
      self,
      InitiateReload(userList))*/
  private val listener = context.system.actorOf(Props(new Actor {
    def receive = {
      case e @ LoginEvent(identity, request) => println(e)
      case e @ LogoutEvent(identity, request) => println(e)
      case e @ SignUpEvent(identity, request) => println(e)
      case e @ NotAuthenticatedEvent(request) => println(e)
      case e @ AuthenticatedEvent(identity, request) => println(e)
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

}

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
trait MyPrioQueueSemantics

// This is the MessageQueue implementation
class UniquePriorityMessageQueue extends MessageQueue
  with MyPrioQueueSemantics {

  private final val queue = new UniquePriorityBlockingQueue[Envelope](500, Ordering.by(e => e.message match {
    case m: InfoUpdaterMessage => { println(s"steaminfoupdater:210 $m"); m.priority }
    case _ => Int.MaxValue
  }))

  // these should be implemented; queue used as example
  def enqueue(receiver: ActorRef, handle: Envelope): Unit = {
    queue.add(handle)
    println(s"steaminfoupdater:217 $handle")
    println(s"steaminfoupdater:218 ${queue.toArray.toList}")
    println(s"steaminfoupdater:219 ${queue.size()}")
  }

  def dequeue(): Envelope = {
    println(s"steaminfoupdater:223 ${queue.size()}")
    println(s"steaminfoupdater:224 ${queue.peek()}")
    queue.poll()
  }

  def numberOfMessages: Int = queue.size

  def hasMessages: Boolean = !queue.isEmpty

  def cleanUp(owner: ActorRef, deadLetters: MessageQueue) {
    while (hasMessages) {
      deadLetters.enqueue(owner, dequeue())
    }
  }
}

class UniquePriorityBlockingQueue[T](initialCapacity: Int, ordering: Ordering[T]) extends ForwardingQueue[T] {
  override val delegate: util.Queue[T] = new PriorityBlockingQueue[T](initialCapacity, ordering)
  //override def delegate(): util.Queue[T] =

  override def add(element: T): Boolean = if (super.contains(element)) {
    println(s"steaminfoupdater:243 $element")
    false
  } else {
    println(s"steaminfoupdater:246 $element")
    delegate.add(element)
  }

  override def offer(o: T): Boolean = standardOffer(o)
}

// This is the Mailbox implementation
class UniquePriorityMailbox extends MailboxType with ProducesMessageQueue[UniquePriorityMessageQueue] {

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