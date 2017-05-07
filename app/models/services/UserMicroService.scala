package models.services

import java.util.UUID

import com.mohiva.play.silhouette.api.services.IdentityService
import com.mohiva.play.silhouette.impl.providers.CommonSocialProfile
import models.{ ServiceProfile, User }

import scala.concurrent.Future

/**
 * Handles actions to users.
 */
trait UserMicroService[S <: ServiceProfile] extends IdentityService[S] {
  def tag: String

  /**
   * Saves a user.
   *
   * @param user The user to save.
   * @return The saved user.
   */
  def save(user: S): Future[S]

}
