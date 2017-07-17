package models

import com.mohiva.play.silhouette.api.Identity
import com.mohiva.play.silhouette.impl.providers.SocialProfile
import reactivemongo.bson.{ BSONDocument, BSONDocumentReader, BSONDocumentWriter, BSONNumberLike }

/**
 * Created by henrik on 2017-02-23.
 */
trait ServiceProfile extends Identity with SocialProfile {
  type Self <: ServiceProfile
  import models.ServiceProfile._
  def id: ServiceUserId
  def service: String
  def visible: Boolean
  def displayName: String
  def avatarUrl: String
  def profileState: ProfileState
  def isInGame: Boolean
  def currentlyPlaying: Game
  def isRegistered: Boolean
  def register: Self

  override def equals(obj: scala.Any): Boolean = obj match {
    case s: ServiceProfile => s.id == id && s.service == service
    case _ => false
  }
}

object ServiceProfile {
  implicit def ordering: Ordering[ServiceProfile] = Ordering.by { p: ServiceProfile => { (p.visible, p.currentlyPlaying, p.profileState, p.displayName) } }
  implicit def ordering2: Ordering[ProfileState] = Ordering.by(s => s.priority)
  type ServiceUserId = String
  sealed trait ProfileState {
    def priority: Int
  }
  case object Online extends ProfileState { override def priority = 4 }
  case object Offline extends ProfileState { override def priority = 20 }
  case object Busy extends ProfileState { override def priority = 8 }
  case object Away extends ProfileState { override def priority = 6 }
  case object Snooze extends ProfileState { override def priority = 10 }
  case object LookingToTrade extends ProfileState { override def priority = 2 }
  case object LookingToPlay extends ProfileState { override def priority = 0 }

  implicit object ProfileStateWriter extends BSONDocumentWriter[ProfileState] {
    def write(state: ProfileState): BSONDocument = BSONDocument("code" -> stateCode(state))
  }
  implicit object ProfileStateReader extends BSONDocumentReader[ProfileState] {
    override def read(bson: BSONDocument): ProfileState = profileStateFactory(bson.getAs[BSONNumberLike]("code").get.toInt)
  }
  def stateCode(state: ProfileState): Int = state match {
    case Offline => 0
    case Online => 1
    case Busy => 2
    case Away => 3
    case Snooze => 4
    case LookingToTrade => 5
    case LookingToPlay => 6
  }
}
