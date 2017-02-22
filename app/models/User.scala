package models

import java.util.UUID

import com.mohiva.play.silhouette.api.{ Identity, LoginInfo }

/**
 * The user object.
 *
 * @param userID The unique ID of the user.
 * @param loginInfo The linked login info.
 * @param avatarURL Maybe the avatar URL of the authenticated provider.
 * @param activated Indicates that the user has activated its registration.
 */
case class User(
                 userID: UUID,
                 loginInfo: LoginInfo,
                 avatarURL: Option[String],
                 activated: Boolean) extends Identity {

  /**
    * Tries to construct a name.
    *
    * @return Maybe a name.
    */
  def name = Some(loginInfo.providerKey) //steamID
}

