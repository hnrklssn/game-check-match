package utils.auth

import com.mohiva.play.silhouette.api.LoginInfo
import models.daos.SteamUserDAO.SteamId

import scala.language.implicitConversions

/**
 * Created by Henrik on 2017-04-25.
 */

/**
 * Extracts the Steam id from the URL returned with OpenID
 * @param loginInfo
 */
class LoginInfoConverter(loginInfo: LoginInfo) {
  import LoginInfoConverters._
  def toId: SteamId = extractId(loginInfo.providerKey)
}
class SteamIdConverter(steamId: SteamId) {
  private val steamUrl = "http://steamcommunity.com/openid/id/"
  def toLoginInfo: LoginInfo = new LoginInfo("Steam", steamUrl + steamId)

}

object LoginInfoConverters {
  implicit def loginInfoDecoration(loginInfo: LoginInfo): LoginInfoConverter = new LoginInfoConverter(loginInfo)
  implicit def steamIdDecoration(steamId: SteamId): SteamIdConverter = new SteamIdConverter(steamId)
  val steamRegex = raw"steamcommunity.com/openid/id/(\d+)".r("id")
  def extractId(url: String): SteamId = {
    println(s"$url LoginInfoConverter.scala")
    steamRegex.findFirstMatchIn(url).get.group("id")
  }
}
