package models.services

import javax.inject.Inject

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.services.IdentityService
import models.ServiceProfile

import scala.concurrent.Future

/**
 * Created by Henrik on 2017-05-05.
 */
class UserService @Inject() (userMicroServiceRegistry: UserMicroServiceRegistry) extends IdentityService[ServiceProfile] {
  override def retrieve(loginInfo: LoginInfo): Future[Option[ServiceProfile]] = userMicroServiceRegistry.getUserMicroService[ServiceProfile](loginInfo.providerID).map(_.retrieve(loginInfo)).getOrElse(Future.successful(None)) //Future.sequence(userMicroServiceRegistry.services.map {s => s.retrieve(loginInfo)}).map(_.flatten.headOption)
  def save[T <: ServiceProfile](profile: T): Future[T] = userMicroServiceRegistry.getUserMicroService[T](profile.service).map(_.save(profile)).getOrElse(Future.failed(new NoSuchElementException("None.get")))
}

case class UserMicroServiceRegistry @Inject() (services: Seq[UserMicroService[_]]) {
  def getUserMicroService[T <: ServiceProfile](tag: String): Option[UserMicroService[T]] = {
    println(s"$tag UserService.scala")
    services.foreach(s => println(s.tag))
    services.find(_.tag == tag).map(_.asInstanceOf[UserMicroService[T]])
  }
}
