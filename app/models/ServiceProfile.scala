package models

/**
 * Created by henrik on 2017-02-23.
 */
trait ServiceProfile {
  import models.ServiceProfile._
  def id: serviceUserId
  def service: String
  def visible: Boolean
  def displayName: String
  def avatarUrl: String
  def profileState: ProfileState
}

object ServiceProfile {
  type serviceUserId = String
  trait ProfileState
  case object Online extends ProfileState
  case object Offline extends ProfileState
  case object Busy extends ProfileState
  case object Away extends ProfileState
  case object Snooze extends ProfileState
  case object LookingToTrade extends ProfileState
  case object LookingToPlay extends ProfileState
}
