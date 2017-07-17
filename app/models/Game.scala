package models

import models.Game._
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter}

import scala.collection.JavaConverters._

/**
 * Created by henrik on 2017-02-22.
 */
sealed trait Game {
  def isDefined: Boolean
  def id: GameId
  def iconUrl: String
  def logoUrl: String

  //def cypher = s""":Game {id: "${id}", name: "$toString", imageUrl: "$imageUrl", isDefined: "$isDefined"}"""
}
case class SomeGame(id: GameId, name: String, iconUrl: String, logoUrl: String) extends Game {
  override def toString: String = name
  override def isDefined: Boolean = true
}
case object NoGame extends Game {
  override def id = ""
  override def toString: String = "Nothing"
  override def isDefined: Boolean = false
  override def iconUrl: String = defaultImageUrl
  override def logoUrl = defaultImageUrl
}
object Game {
  implicit def ordering: Ordering[Game] = Ordering.by(g => (g.isDefined, g.toString))
  def apply(id: GameId, name: String, imageUrl: String, logoUrl: String): Game = {
    if (id == null || id == "" || name == null || name == "") { NoGame }
    else if (imageUrl == null || imageUrl.isEmpty || logoUrl == null || logoUrl.isEmpty) {
      SomeGame(id, name, defaultImageUrl, defaultImageUrl)
    } else { SomeGame(id, name, imageUrl, logoUrl) }
  }
  def fromApiModel(g: com.lukaspradel.steamapi.data.json.ownedgames.Game): Game = {
    g.getAdditionalProperties.asScala.foreach(t => println(s"additionalprop: $t"))
    apply(g.getAppid.toString, g.getName, g.getImgIconUrl, g.getImgLogoUrl)
  }
  val defaultImageUrl = "placeholder.png"
  type GameId = String

  implicit object Writer extends BSONDocumentWriter[Game] {
    def write(game: Game): BSONDocument = BSONDocument(
      "isDefined" -> game.isDefined,
      "id" -> game.id,
      "name" -> game.toString,
      "iconUrl" -> game.iconUrl,
      "logoUrl" -> game.logoUrl
    )
  }
  implicit object Reader extends BSONDocumentReader[Game] {
    override def read(bson: BSONDocument): Game = apply(
      bson.getAs[String]("id").get,
      bson.getAs[String]("name").get,
      bson.getAs[String]("iconUrl").get,
      bson.getAs[String]("logoUrl").get
    )
  }
}