package models

import Game._
/**
 * Created by henrik on 2017-02-22.
 */
case class Game(id: GameId, name: String) {

}

object Game {
  type GameId = String
}