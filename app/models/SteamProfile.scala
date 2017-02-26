package models

import com.lukaspradel.steamapi.data.json.playersummaries.Player

/**
 * Created by henrik on 2017-02-26.
 */
trait SteamProfile extends ServiceProfile {

  def service: String = "Steam"
}

trait SteamProfileFactory {
  def apply(profileData: Player): SteamProfile
}