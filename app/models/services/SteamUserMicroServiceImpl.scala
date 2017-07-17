package models.services

import javax.inject.Inject

import com.mohiva.play.silhouette.api.LoginInfo
import models.SteamProfile
import models.daos.SteamProfileDAO
import utils.auth.LoginInfoConverters._

import scala.concurrent.Future

/**
 * Handles actions to users.
 *
 * @param userDAO The user DAO implementation.
 */
class SteamUserMicroServiceImpl @Inject() (userDAO: SteamProfileDAO) extends UserMicroService[SteamProfile] {

  /**
   * Retrieves a user that matches the specified login info.
   *
   * @param loginInfo The login info to retrieve a user.
   * @return The retrieved user or None if no user could be retrieved for the given login info.
   */
  override def retrieve(loginInfo: LoginInfo): Future[Option[SteamProfile]] = userDAO.find(loginInfo.toId)

  /**
   * Saves a user.
   *
   * @param user The user to save.
   * @return The saved user.
   */
  override def save(user: SteamProfile) = userDAO.save(user)

  /*def save(profile: CommonSocialProfile) = {
    userDAO.find(profile.loginInfo).flatMap {
      case Some(user) => // Update user with profile
        userDAO.save(user.copy(
          avatarURL = profile.avatarURL
        ))
      case None => // Insert a new user
        userDAO.save(User(
          userID = UUID.randomUUID(),
          loginInfo = profile.loginInfo,
          avatarURL = profile.avatarURL,
          activated = true
        ))
    }
  }*/
  override def tag: String = {
    //println("!! " + SteamProvider.ID)
    //SteamProvider.ID
    "Steam"
  }
}
