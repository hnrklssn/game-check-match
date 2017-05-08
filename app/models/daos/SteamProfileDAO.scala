package models.daos

import java.util.UUID
import javax.inject.Inject

import akka.actor.ActorSystem
import akka.http.scaladsl.model.DateTime
import com.mohiva.play.silhouette.api.LoginInfo
import com.sun.glass.ui.InvokeLaterDispatcher
import models.daos.SteamUserDAO.SteamId
import models.{ ServiceProfile, SteamProfile, User }

import scala.concurrent.{ ExecutionContext, Future, Promise }
import play.api.libs.json._
import play.api.mvc._
import reactivemongo.api.{ Cursor, ReadPreference }
import reactivemongo.play.json._
import reactivemongo.play.json.collection._
import play.modules.reactivemongo._
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.{ Upserted, WriteError }
import reactivemongo.bson._

import scala.collection.generic.CanBuildFrom
import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.parsing.json.{ JSONArray, JSONObject }

/**
 * Created by henrik on 2017-03-27.
 */
class SteamProfileDAO @Inject() (val reactiveMongoApi: ReactiveMongoApi)(implicit exec: ExecutionContext, system: ActorSystem) extends MongoController with ReactiveMongoComponents {
  implicit def profileReader: reactivemongo.bson.BSONDocumentReader[SteamProfile] = SteamProfile.Reader
  implicit def profileWriter: reactivemongo.bson.BSONDocumentWriter[SteamProfile] = SteamProfile.Writer

  def profilesFuture: Future[BSONCollection] = database.map(_.collection[BSONCollection]("profiles"))

  /**
   * Finds a user by its user ID.
   *
   * @param userID The ID of the user to find.
   * @return The found user or None if no user for the given ID could be found.
   */
  def find(userID: SteamId): Future[Option[SteamProfile]] = {
    val futureUsersList: Future[Option[SteamProfile]] = profilesFuture.flatMap {
      // find all cities with name `name`
      _.find(Json.obj("id" -> userID))
        // perform the query and get a cursor of JsObject
        .cursor[SteamProfile](ReadPreference.primary).headOption
      // Coollect the results as a list
      //.collect[Option](1, Cursor.ContOnError[Option[ServiceProfile]] { (o: Option[ServiceProfile], t: Throwable) => println(t.getMessage) })
    }
    futureUsersList
  }

  def bulkFind(users: List[SteamId]): Future[List[SteamProfile]] = {
    profilesFuture.flatMap { collection =>
      collection.find(BSONDocument("id" -> BSONDocument("$in" -> users)))
        .cursor[SteamProfile](ReadPreference.primary)
        .collect[List](users.size, Cursor.FailOnError[List[SteamProfile]]())
    }
  }

  def findAllUsers: Future[List[String]] = {
    profilesFuture.flatMap { collection =>
      // only fetch the id field for the result documents
      val projection = BSONDocument("id" -> 1)
      collection.find(BSONDocument("isRegistered" -> true), projection)
        .cursor[BSONDocument](ReadPreference.primary)
        .collect[List](1000, Cursor.FailOnError[List[BSONDocument]]())
    }.map { list =>
      list.map(_.getAs[String]("id").get)
    }
  }

  /**
   * Saves a user.
   *
   * @param profile The user to save.
   * @return The saved user.
   */
  def save(profile: SteamProfile): Future[SteamProfile] = {
    println(s"saving $profile")
    val writeFuture = if (profile.isRegistered) {
      profilesFuture.flatMap(_.update[BSONDocument, BSONDocument](BSONDocument("id" -> profile.id), BSONDocument("$set" -> BSONDocument(BSONDocument(profileWriter.write(profile)), BSONDocument("isRegistered" -> true))), upsert = true))
    } else {
      profilesFuture.flatMap(_.update[BSONDocument, BSONDocument](BSONDocument("id" -> profile.id), BSONDocument("$setOnInsert" -> BSONDocument("isRegistered" -> profile.isRegistered), "$set" -> profile), upsert = true))
    }
    writeFuture.map(_ => profile)
  }

  private var promise: Option[Promise[Seq[SteamProfile]]] = None
  private var timeStamp: Option[DateTime] = None
  private val profileBuffer = mutable.Set[SteamProfile]()

  def bufferedSave(profiles: Seq[SteamProfile], maxBufferSize: Int = 200, maxWait: FiniteDuration = 5.seconds): Future[Seq[SteamProfile]] = {
    if (profileBuffer.size > maxBufferSize) {
      flushBuffer()
    }
    profileBuffer ++= profiles
    if (promise.isEmpty) {
      promise = Some(Promise[Seq[SteamProfile]]())
      val permTimeRef = DateTime.now //saved in separate variable to compare to timeStamp in future
      timeStamp = Some(permTimeRef)
      akka.pattern.after(maxWait, system.scheduler) {
        Future {
          if (timeStamp.forall(_.equals(permTimeRef))) {
            flushBuffer()
          }
        }
      }
    }
    promise.get.future.map(_.filter(p => profiles.contains(p)))
  }

  def flushBuffer() = {
    timeStamp = None
    promise.map(_.completeWith(Future.sequence(profileBuffer.map(save).toList)))
    promise = None
    profileBuffer.clear()
  }

}
