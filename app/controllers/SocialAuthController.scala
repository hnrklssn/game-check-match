package controllers

import javax.inject.Inject

import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.exceptions.ProviderException
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers._
import models.ServiceProfile
import models.services.{ ServiceProfileBuilder, UserService }
import play.api.i18n.{ I18nSupport, Messages, MessagesApi }
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{ Action, Controller, Cookie }
import utils.auth.DefaultEnv

import scala.concurrent.Future

/**
 * The social auth controller.
 *
 * @param messagesApi The Play messages API.
 * @param silhouette The Silhouette stack.
 * @param userService The user service implementation.
 * @param authInfoRepository The auth info service implementation.
 * @param socialProviderRegistry The social provider registry.
 * @param webJarAssets The webjar assets implementation.
 */
class SocialAuthController @Inject() (
  val messagesApi: MessagesApi,
  silhouette: Silhouette[DefaultEnv],
  userService: UserService,
  authInfoRepository: AuthInfoRepository,
  socialProviderRegistry: SocialProviderRegistry,
  implicit val webJarAssets: WebJarAssets)
  extends Controller with I18nSupport with Logger {

  /**
   * Authenticates a user against a social provider.
   *
   * @param provider The ID of the provider to authenticate against.
   * @return The result to display.
   */
  def authenticate(provider: String) = Action.async { implicit request =>
    (socialProviderRegistry.get[SocialProvider](provider) match {
      case Some(p: SocialProvider with ServiceProfileBuilder) =>
        p.authenticate().flatMap {
          case Left(result) => {println(s"socialAuthCont:47 $result");Future.successful(result)}
          case Right(authInfo) => for {
            profile <- p.retrieveProfile(authInfo).map(_.register)
            new_user <- userService.retrieve(profile.loginInfo).map{p => println(s"socialAuthCont:50 $p"); p}.map(_.isEmpty)
            user <- userService.save(profile)
            authInfo <- authInfoRepository.save(profile.loginInfo, authInfo)
            authService <- Future(silhouette.env.authenticatorService)
            authenticator <- authService.create(profile.loginInfo)
            value <- silhouette.env.authenticatorService.init(authenticator)
            result <- silhouette.env.authenticatorService.embed(value, Redirect(routes.ApplicationController.index()))
          } yield {
            println(s"RIGHHT $authInfo")
            println(result.withCookies(new Cookie("test", "test")).header)
            if (new_user) {
              silhouette.env.eventBus.publish(SignUpEvent(user, request))
            }
            silhouette.env.eventBus.publish(LoginEvent(user, request))
            result.flashing("success" -> s"Welcome, ${user.displayName}!")
          }
        }
      case _ => Future.failed(new ProviderException(s"Cannot authenticate with unexpected social provider $provider"))
    }).recover {
      case e: ProviderException =>
        logger.error("Unexpected provider error", e)
        Redirect(routes.SignInController.view()).flashing("error" -> Messages("could.not.authenticate"))
    }
  }
}
