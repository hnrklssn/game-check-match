package models

import com.lukaspradel.steamapi.data.json.playersummaries.Player
import models.ServiceProfile.ProfileState
import reactivemongo.bson.{ BSONDocument, BSONDocumentReader, BSONDocumentWriter }

/**
 * Created by henrik on 2017-02-26.
 */
trait SteamProfile extends ServiceProfile {
  override type Self = SteamProfile
  def service: String = "Steam"
}

trait SteamProfileFactory {
  def apply(profileData: Player): SteamProfile
}

object SteamProfile {
  implicit object Writer extends BSONDocumentWriter[SteamProfile] {
    def write(profile: SteamProfile): BSONDocument = BSONDocument(
      "id" -> profile.id,
      "service" -> profile.service,
      "visible" -> profile.visible,
      "displayName" -> profile.displayName,
      "avatarUrl" -> profile.avatarUrl,
      "profileState" -> profile.profileState,
      "isInGame" -> profile.isInGame,
      "currentlyPlaying" -> profile.currentlyPlaying
    )
  }

  implicit object Reader extends BSONDocumentReader[SteamProfile] {
    def read(doc: BSONDocument): SteamProfile = {
      println("asdfADSFGAA  SDFAG")
      SteamProfileImpl(
        doc.getAs[String]("id").get,
        doc.getAs[Boolean]("visible").get,
        doc.getAs[String]("displayName").get,
        doc.getAs[String]("avatarUrl").get,
        doc.getAs[ProfileState]("profileState").get,
        doc.getAs[Game]("currentlyPlaying").get,
        doc.getAs[Boolean]("isRegistered").get
      )
    }
  }
}