package utils.auth

import com.mohiva.play.silhouette.api.Env
import com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticator
import models.ServiceProfile

/**
 * The default env.
 */
trait DefaultEnv extends Env {
  type I = ServiceProfile
  type A = SessionAuthenticator
}
