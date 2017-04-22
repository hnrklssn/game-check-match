package models.daos

import java.util.UUID
import javax.inject.Inject

import com.mohiva.play.silhouette.api.LoginInfo
import com.sun.glass.ui.InvokeLaterDispatcher
import models.daos.SteamUserDAO.SteamId
import models.{ ServiceProfile, User }

import scala.concurrent.Future
import play.api.libs.json._
import play.api.mvc._
import reactivemongo.api.{ Cursor, ReadPreference }
import reactivemongo.play.json._
import reactivemongo.play.json.collection._
import play.modules.reactivemongo._
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.WriteError
import reactivemongo.bson._

import scala.collection.generic.CanBuildFrom
import scala.collection.mutable
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.parsing.json.{ JSONArray, JSONObject }

/**
 * Created by henrik on 2017-03-27.
 */
class ServiceProfileDAO @Inject() (val reactiveMongoApi: ReactiveMongoApi)(implicit exec: ExecutionContext) extends MongoController with ReactiveMongoComponents {
  implicit def profileReader: reactivemongo.bson.BSONDocumentReader[ServiceProfile] = ServiceProfile.Reader
  implicit def profileWriter: reactivemongo.bson.BSONDocumentWriter[ServiceProfile] = ServiceProfile.Writer

  def profilesFuture: Future[BSONCollection] = database.map(_.collection[BSONCollection]("profiles"))

  /**
   * Finds a user by its login info.
   *
   * @param loginInfo The login info of the user to find.
   * @return The found user or None if no user for the given login info could be found.
   */
  def find(loginInfo: LoginInfo): Future[Option[User]] = ???

  /**
   * Finds a user by its user ID.
   *
   * @param userID The ID of the user to find.
   * @return The found user or None if no user for the given ID could be found.
   */
  def find(userID: SteamId): Future[Option[ServiceProfile]] = {
    val futureUsersList: Future[Option[ServiceProfile]] = profilesFuture.flatMap {
      // find all cities with name `name`
      _.find(Json.obj("id" -> userID))
        // perform the query and get a cursor of JsObject
        .cursor[ServiceProfile](ReadPreference.primary).headOption
      // Coollect the results as a list
      //.collect[Option](1, Cursor.ContOnError[Option[ServiceProfile]] { (o: Option[ServiceProfile], t: Throwable) => println(t.getMessage) })
    }
    futureUsersList
  }

  def bulkFind(users: List[SteamId]): Future[List[ServiceProfile]] = {
    profilesFuture.flatMap { collection =>
      collection.find(BSONDocument("id" -> BSONDocument("$in" -> users)))
        .cursor[ServiceProfile](ReadPreference.primary)
        .collect[List](users.size, Cursor.FailOnError[List[ServiceProfile]]())
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
  def save(profile: ServiceProfile): Future[Seq[WriteError]] = {
    println(s"saving $profile")
    profilesFuture.flatMap(_.update[BSONDocument, ServiceProfile](BSONDocument("id" -> profile.id), profile, upsert = true)).map(_.writeErrors)
  }

  def registerUser(profile: ServiceProfile): Future[Seq[WriteError]] = {
    println(s"adding user $profile")
    profilesFuture.flatMap(_.update[BSONDocument, BSONDocument](BSONDocument("id" -> profile.id), BSONDocument("$set" -> profile), upsert = true))
    profilesFuture.flatMap(_.update[BSONDocument, BSONDocument](BSONDocument("id" -> profile.id), BSONDocument("$set" -> BSONDocument("isRegistered" -> true)))).map(_.writeErrors)
  }

}
