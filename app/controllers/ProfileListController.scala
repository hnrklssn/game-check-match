package controllers

import javax.inject.Inject

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, Graph, SourceShape }
import akka.stream.scaladsl.{ Sink, Source }
import com.mohiva.play.silhouette.api.{ LogoutEvent, Silhouette }
import com.mohiva.play.silhouette.impl.providers.{ SocialProvider, SocialProviderRegistry }
import models.ServiceProfile
import models.daos.ServiceUserDAO.ServiceProfileSource
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.mvc.{ Action, Controller }
import utils.auth.DefaultEnv

import scala.concurrent.Future
/**
 * Created by henrik on 2017-02-24.
 */
class ProfileListController @Inject() (profiles: ServiceProfileSource, silhouette: Silhouette[DefaultEnv], val messagesApi: MessagesApi, implicit val webJarAssets: WebJarAssets) extends Controller with I18nSupport {
  import controllers.ProfileListController._
  import scala.concurrent.ExecutionContext.Implicits.global

  def users = Action.async { implicit request =>
    implicit val system = ActorSystem("actor-system")
    implicit val materializer = ActorMaterializer()
    val mapByServiceSource = profiles.map { xs: Seq[ServiceProfile] =>
      (
        xs.headOption.map(_.service).getOrElse("Unknown service provider"),
        xs
      )
    }
    val mapByServiceFuture = mapByServiceSource.runFold(Map[String, Seq[ServiceProfile]]()) { (existingMap, tuple) =>
      existingMap + tuple
    }
    mapByServiceFuture.map(m => Ok(views.html.inGame(m)))
  }
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
