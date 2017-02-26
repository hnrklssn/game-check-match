package models.daos

import javax.inject.Inject

import akka.NotUsed
import akka.stream.scaladsl.Source
import models.Game._
import models.{ SteamProfile, SteamProfileFactory }

/**
 * Created by henrik on 2017-02-22.
 */

trait SteamUserDAO {
  import models.daos.SteamUserDAO._

  def userSummaries(ids: List[SteamId], steamProfileFactory: SteamProfileFactory): Source[Seq[SteamProfile], NotUsed]

  def userStatus(id: SteamId): String

  def currentGame(id: SteamId): Option[GameId]

}

object SteamUserDAO {
  type SteamId = ServiceUserDAO.serviceUserId
}
