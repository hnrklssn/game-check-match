package models.daos

import javax.inject.Inject

import models.Game.GameId
import models.{ Game }
import play.api.libs.json.Json
import play.modules.reactivemongo.{ MongoController, ReactiveMongoApi, ReactiveMongoComponents }
import reactivemongo.api.{ Cursor, ReadPreference }
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.WriteError
import reactivemongo.bson.BSONDocument

import scala.concurrent.{ ExecutionContext, Future }

/**
 * Created by henrik on 2017-04-07.
 */
class GameDAOImpl @Inject() (val reactiveMongoApi: ReactiveMongoApi)(implicit exec: ExecutionContext) extends GameDAO with MongoController with ReactiveMongoComponents {
  implicit def profileReader: reactivemongo.bson.BSONDocumentReader[Game] = Game.Reader
  implicit def profileWriter: reactivemongo.bson.BSONDocumentWriter[Game] = Game.Writer

  private def gamesFuture: Future[BSONCollection] = database.map(_.collection[BSONCollection]("games"))

  /**
   * Finds a game by its game ID.
   *
   * @param gameID The ID of the game to find.
   * @return The found game or None if no game for the given ID could be found.
   */
  def find(gameID: GameId): Future[Option[Game]] = {
    val futureUsersList: Future[Option[Game]] = gamesFuture.flatMap {
      // find all games with id `gameID`
      _.find(BSONDocument("id" -> gameID))
        // perform the query and get a cursor of Games
        .cursor[Game](ReadPreference.primary).headOption
    }
    futureUsersList
  }

  def bulkFind(games: List[GameId]): Future[List[Game]] = {
    gamesFuture.flatMap { collection =>
      collection.find(BSONDocument("id" -> BSONDocument("$in" -> games)))
        .cursor[Game](ReadPreference.primary)
        .collect[List](games.size, Cursor.FailOnError[List[Game]]())
    }
  }

  def findAllGameIds: Future[List[GameId]] = {
    gamesFuture.flatMap { collection =>
      // only fetch the id field for the result documents
      val projection = BSONDocument("id" -> 1)
      collection.find(BSONDocument(), projection)
        .cursor[BSONDocument](ReadPreference.primary)
        .collect[List](1000, Cursor.FailOnError[List[BSONDocument]]())
    }.map { list =>
      list.map(_.getAs[String]("id").get)
    }
  }

  /**
   * Inserts a game, and updates the information if already existent.
   *
   * @param game The game to save.
   * @return Error codes.
   */
  def upsert(game: Game): Future[Seq[WriteError]] = {
    println(s"saving game $game")
    gamesFuture.flatMap(_.update[BSONDocument, Game](BSONDocument("id" -> game.id), game, upsert = true)).map(_.writeErrors)
  }

}
