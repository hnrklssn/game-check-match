package models.daos

import com.lukaspradel.steamapi.data.json.friendslist.GetFriendList
import com.lukaspradel.steamapi.data.json.ownedgames.GetOwnedGames
import com.lukaspradel.steamapi.data.json.playersummaries.GetPlayerSummaries
import models.Game._
import models.{ Game, SteamProfile }

/**
 * Created by henrik on 2017-02-22.
 */

trait SteamUserDAO {
  import models.daos.SteamUserDAO._

  //  def userSummaries(ids: List[SteamId]): Source[Seq[SteamProfile], NotUsed]
  def getUserSummaries(ids: List[SteamId]): GetPlayerSummaries
  def processSummaries(summaries: GetPlayerSummaries): Seq[SteamProfile]
  //def bufferFetchProfiles(ids: Iterable[SteamId]): Future[Seq[SteamProfile]]
  def getOwnedGames(id: SteamId): GetOwnedGames
  def processGames(games: GetOwnedGames): Seq[(Game, Int, Int)]
  def getFriends(id: SteamId): GetFriendList
  def processFriends(friends: GetFriendList): Seq[(SteamId, Int)]

  def userStatus(id: SteamId): String

  def currentGame(id: SteamId): Option[GameId]

}

object SteamUserDAO {
  type SteamId = ServiceUserDAO.serviceUserId
}
