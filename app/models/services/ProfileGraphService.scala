package models.services

import models.Game.GameId
import models.daos.SteamUserDAO.SteamId
import models.{ Game, SteamProfile }

import scala.util.Try

/**
 * Created by henrik on 2017-03-08.
 */
trait ProfileGraphService {
  def getGames(user: SteamId): Try[Seq[(GameId, Int)]]
  def updateGames(user: SteamId, games: Seq[(Game, Int)])
  def getFriends(user: SteamId): Try[Seq[(SteamId, Int)]]
  def updateFriends(user: SteamId, friends: Seq[(SteamId, Int)])
  def mergeProfile(user: SteamProfile)
}
