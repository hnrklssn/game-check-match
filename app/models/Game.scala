package models

import Game._
import reactivemongo.bson.{ BSONDocument, BSONDocumentReader, BSONDocumentWriter }

/**
 * Created by henrik on 2017-02-22.
 */
sealed trait Game extends NodeEntity {
  def isDefined: Boolean
  def id: GameId
  def imageUrl: String

  override def cypher = s""":Game {id: "${id}", name: "$toString", imageUrl: "$imageUrl", isDefined: "$isDefined"}"""
}
case class SomeGame(id: GameId, name: String, imageUrl: String) extends Game {
  override def toString: String = name
  override def isDefined: Boolean = true
}
case object NoGame extends Game {
  override def id = ""
  override def toString: String = "Nothing"
  override def isDefined: Boolean = false
  override def imageUrl: String = defaultImageUrl
}
object Game {
  implicit def ordering: Ordering[Game] = Ordering.by(g => (g.isDefined, g.toString))
  def apply(id: GameId, name: String, imageUrl: String): Game = {
    if (id == null || id == "" || name == null || name == "") { NoGame }
    else if (imageUrl == null || imageUrl.isEmpty) {
      SomeGame(id, name, defaultImageUrl)
    } else { SomeGame(id, name, imageUrl) }
  }
  def fromApiModel(g: com.lukaspradel.steamapi.data.json.ownedgames.Game): Game = apply(g.getAppid.toString, g.getName, g.getImgIconUrl)
  val defaultImageUrl = "placeholder.png"
  type GameId = String

  implicit object Writer extends BSONDocumentWriter[Game] {
    def write(game: Game): BSONDocument = BSONDocument(
      "isDefined" -> game.isDefined,
      "id" -> game.id,
      "name" -> game.toString,
      "imageUrl" -> game.imageUrl
    )
  }
  implicit object Reader extends BSONDocumentReader[Game] {
    override def read(bson: BSONDocument): Game = apply(
      bson.getAs[String]("id").get,
      bson.getAs[String]("name").get,
      bson.getAs[String]("imageUrl").get
    )
  }
}