package utils.auth

/**
 * Created by Henrik on 2017-04-24.
 */
import play.api.mvc._

import scala.concurrent.Future

class UserRequest[A](val username: Option[String], request: Request[A]) extends WrappedRequest[A](request)

object UserAction extends ActionBuilder[UserRequest] with ActionTransformer[Request, UserRequest] {
  def transform[A](request: Request[A]) = Future.successful {
    new UserRequest(request.session.get("id"), request)
  }
}