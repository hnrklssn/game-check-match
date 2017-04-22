package models
import ServiceProfile._
import com.lukaspradel.steamapi.data.json.playersummaries.Player
import models.Game.GameId
/**
 * Created by henrik on 2017-02-23.
 */
import SteamProfileImpl._
case class SteamProfileImpl(id: steamUserId, visible: Boolean, displayName: String, avatarUrl: String, profileState: ProfileState, currentlyPlaying: Game) extends SteamProfile {
  override def isInGame: Boolean = currentlyPlaying.isDefined

  def cypher = s""":SteamProfile {id: "$id", visible: "$visible", name: "$displayName", avatarUrl: "$avatarUrl"}"""
}

object SteamProfileImpl {
  type steamUserId = serviceUserId
}

class SteamProfileFactoryImpl extends SteamProfileFactory {
  override def apply(profileData: Player): SteamProfile = {
    val id = profileData.getSteamid
    val name = profileData.getPersonaname
    val avatar = profileData.getAvatarmedium
    val state = profileStateFactory(profileData.getPersonastate)
    val visible = profileData.getCommunityvisibilitystate.toInt match {
      case 1 => false
      case 2 => false
      case 3 => true
    }
    val additional = profileData.getAdditionalProperties
    val currentGame: Game = if (additional.containsKey("gameid") && additional.containsKey("gameextrainfo")) {
      Game(additional.get("gameid").asInstanceOf[GameId], additional.get("gameextrainfo").asInstanceOf[String], null, null)
    } else {
      NoGame
    }
    val temp = SteamProfileImpl(id = id, visible = visible, displayName = name, avatarUrl = avatar, profileState = state, currentlyPlaying = currentGame)
    println(temp)
    temp
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