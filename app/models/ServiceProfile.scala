package models

import reactivemongo.bson.{ BSONDocument, BSONDocumentReader, BSONDocumentWriter, BSONNumberLike }

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
  def isInGame: Boolean
  def currentlyPlaying: Game
}

object ServiceProfile {
  import Game.ordering
  implicit def ordering: Ordering[ServiceProfile] = Ordering.by { p: ServiceProfile => { (p.visible, p.currentlyPlaying, p.profileState, p.displayName) } }
  implicit def ordering2: Ordering[ProfileState] = Ordering.by(s => s.priority)
  type serviceUserId = String
  trait ProfileState {
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
    case Online => 1
    case Offline => 2
    case Busy => 3
    case Away => 4
    case Snooze => 5
    case LookingToTrade => 6
    case LookingToPlay => 7
  }

  implicit object Writer extends BSONDocumentWriter[ServiceProfile] {
    def write(profile: ServiceProfile): BSONDocument = BSONDocument(
      "id" -> profile.id,
      "service" -> profile.service,
      "visible" -> profile.visible,
      "displayName" -> profile.displayName,
      "avatarUrl" -> profile.avatarUrl,
      "profileState" -> profile.profileState,
      "isInGame" -> profile.isInGame,
      "currentlyPlaying" -> profile.currentlyPlaying)
  }

  implicit object Reader extends BSONDocumentReader[ServiceProfile] {
    def read(doc: BSONDocument): ServiceProfile = {
      SteamProfileImpl(
        doc.getAs[String]("id").get,
        doc.getAs[Boolean]("visible").get,
        doc.getAs[String]("displayName").get,
        doc.getAs[String]("avatarUrl").get,
        doc.getAs[ProfileState]("profileState").get,
        doc.getAs[Game]("currentlyPlaying").get
      )
    }
  }
}
