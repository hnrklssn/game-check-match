package models

import java.util.UUID

import com.mohiva.play.silhouette.api.LoginInfo
import models.ServiceProfile.ServiceUserId
import org.joda.time.DateTime

/**
 * A token to authenticate a user against an endpoint for a short time period.
 *
 * @param id The unique token ID.
 * @param userID The unique ID of the user the token is associated with.
 * @param expiry The date-time the token expires.
 */
case class AuthToken(
  id: UUID,
  userID: LoginInfo,
  expiry: DateTime)
