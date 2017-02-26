package models
import ServiceProfile._
import com.lukaspradel.steamapi.data.json.playersummaries.Player
/**
 * Created by henrik on 2017-02-23.
 */
import SteamProfileImpl._
case class SteamProfileImpl(id: steamUserId, visible: Boolean, displayName: String, avatarUrl: String, profileState: ProfileState) extends SteamProfile

object SteamProfileImpl {
  type steamUserId = serviceUserId
}

class SteamProfileFactoryImpl extends SteamProfileFactory {
  override def apply(profileData: Player): SteamProfile = {
    val id = profileData.getSteamid
    val name = profileData.getPersonaname
    val avatar = profileData.getAvatarmedium
    val state = profileStateFactory(profileData.getProfilestate)
    val visible = profileData.getCommunityvisibilitystate.toInt match {
      case 1 => false
      case 3 => true
    }
    SteamProfileImpl(id = id, visible = visible, displayName = name, avatarUrl = avatar, profileState = state)
  }
}

object profileStateFactory {
  def apply(i: Int): ProfileState = i match {
    case 0 => Offline
    case 1 => Online
    case 2 => Busy
    case 3 => Away
    case 4 => Snooze
    case 5 => LookingToTrade
    case 6 => LookingToPlay
  }
}