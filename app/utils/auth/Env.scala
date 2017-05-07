package utils.auth

import com.mohiva.play.silhouette.api.Env
import com.mohiva.play.silhouette.impl.authenticators.{ CookieAuthenticator, SessionAuthenticator }
import models.{ ServiceProfile, User }

/**
 * The default env.
 */
trait DefaultEnv extends Env {
  type I = ServiceProfile
  type A = SessionAuthenticator
}
