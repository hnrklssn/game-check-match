package models.daos

import reactivemongo.api.commands.WriteError

import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * Created by henrik on 2017-02-22.
 */
trait GameDAO {
  import models.Game._
  import models.Game
  def find(gameID: GameId): Future[Option[Game]]
  def bulkFind(games: List[GameId]): Future[List[Game]]
  def findAllGameIds: Future[List[GameId]]
  def upsert(game: Game): Future[Game]
  def bufferedSave(games: Seq[Game], maxBufferSize: Int = 1000, maxWait: FiniteDuration = 30.seconds): Future[Seq[Game]]
}

