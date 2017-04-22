package controllers

import javax.inject.Inject

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{ Sink, Source }
import com.mohiva.play.silhouette.api.{ LogoutEvent, Silhouette }
import com.mohiva.play.silhouette.impl.providers.{ SocialProvider, SocialProviderRegistry }
import models.ServiceProfile.{ Offline, Online }
import models.daos.ServiceProfileDAO
import models.{ ServiceProfile, SteamProfileImpl }
import models.daos.ServiceUserDAO.ServiceProfileSource
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.mvc.{ Action, AnyContent, Controller, Result }
import utils.auth.DefaultEnv

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent._
import scala.util.Success
/**
 * Created by henrik on 2017-02-24.
 */
class ProfileListController @Inject() (profileDAO: ServiceProfileDAO, silhouette: Silhouette[DefaultEnv], val messagesApi: MessagesApi, implicit val webJarAssets: WebJarAssets) extends Controller with I18nSupport {
  import controllers.ProfileListController._
  //import scala.concurrent.ExecutionContext.Implicits.global
  import play.api.libs.concurrent.Execution.Implicits.defaultContext
  implicit val system = ActorSystem("actor-system")
  implicit val materializer = ActorMaterializer()

  def users(profiles: Seq[ServiceProfile]) = Action.async { implicit request =>
    println("Ping!")
    val mapByServiceFuture = profileDAO.findAllUsers
      .flatMap(l => profileDAO.bulkFind(l))
      .map(ps => Map("Steam" -> ps))
    mapByServiceFuture.map { m => Ok(views.html.inGame(m)) }
  }

  /*protected def materializeToMap(profiles: ServiceProfileSource)(implicit mat: Materializer): Future[Map[String, Seq[ServiceProfile]]] = {
    val mapByServiceSource = profiles.map { xs: Seq[ServiceProfile] =>
      val temp = (
        xs.headOption.map(_.service).getOrElse("Unknown service provider"),
        xs
      )
      //println(s"Temp: $temp")
      temp
    }.throttle(1, per = 3.seconds, 3, ThrottleMode.Shaping)
    mapByServiceSource.runFold(Map[String, Seq[ServiceProfile]]()) { (existingMap, tuple) =>
      //println(s"Existing map: $existingMap Tuple: $tuple")
      existingMap + tuple
    }
  }*/
  /*def users = silhouette.SecuredAction.async { implicit request =>
    Future.successful(Ok(views.html.home(request.identity)))
  }*/
  /*private def toMapSink: Sink[Stream[(String, Seq[ServiceProfile])], Map[String, Seq[ServiceProfile]]] = {
    Sink.lastOption
  }*/
}
object ProfileListController {
  def socials = Source(scala.collection.immutable.Seq("Steam"))
  type SocialProfileStream = String
}
