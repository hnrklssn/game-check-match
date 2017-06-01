package models.services

import models.Game.GameId
import models.daos.SteamUserDAO.SteamId
import models.{ Game, SteamProfile }

import scala.reflect.internal.Trees
import scala.concurrent.Future

/**
 * Created by henrik on 2017-03-08.
 */
trait ProfileGraphService {
  def getGames(user: SteamId): Future[Seq[(GameId, Int, Int)]]
  def updateGames(user: SteamId, games: Seq[(Game, Int, Int)])
  def getFriends(user: SteamId): Future[Seq[(SteamId, Int)]]
  def updateFriends(user: SteamId, friends: Seq[(SteamId, Int)])
  def mergeProfile(user: SteamProfile)
  def recentMutualPlayTime(user: SteamId): Future[Seq[(SteamId, GameId)]]
  def mutualTotalGameTime(user: SteamId): Future[Seq[(SteamId, GameId)]]
  def trendingGames(amount: Int): Future[Seq[GameId]]
  def popularAmongFriends(id: SteamId, amount: Int): Future[Seq[(GameId)]]
  def peopleLikeYou(id: SteamId, amount: Int): Future[Seq[SteamId]]
  def popularAmongPeopleLikeYou(id: SteamId, amount: Int): Future[Seq[GameId]]
  def friendsWithGame(id: SteamId, game: GameId): Future[Seq[SteamId]]
  def filterHasGame(steamUsers: Seq[SteamId], gameId: GameId): Future[Seq[SteamId]]
  def mutualFriends(users: Seq[SteamId]): Future[Seq[SteamId]]
}