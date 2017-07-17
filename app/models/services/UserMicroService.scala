package models.services

import com.mohiva.play.silhouette.api.services.IdentityService
import models.ServiceProfile

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
