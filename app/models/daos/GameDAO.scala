package models.daos

/**
 * Created by henrik on 2017-02-22.
 */
trait GameDAO {
  import models.Game._
  import models.Game
  def gameObject(id: GameId): Option[Game]

}

