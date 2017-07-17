/**
 * Copyright 2015 Mohiva Organisation (license at mohiva dot com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package models.services

import com.mohiva.play.silhouette.api.util.HTTPLayer
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.providers.openid.BaseSteamProvider
import models.ServiceProfile
import models.daos.SteamUserDAO
import utils.auth.LoginInfoConverters.extractId

import scala.concurrent.Future

/**
 * The Steam OAuth2 Provider.
 *
 * @param httpLayer The HTTP layer implementation.
 * @param service The OpenID service implementation.
 * @param settings The OpenID provider settings.
 */
class CustomSteamProvider(
  protected val httpLayer: HTTPLayer,
  val service: OpenIDService,
  val settings: OpenIDSettings,
  steamUserDAO: SteamUserDAO)
  extends BaseSteamProvider with ServiceProfileBuilder {

  /**
   * The type of this class.
   */
  override type Self = CustomSteamProvider

  /**
   * The profile parser implementation.
   */
  override val profileParser = new CustomSteamProfileParser(steamUserDAO)

  /**
   * Gets a provider initialized with a new settings object.
   *
   * @param f A function which gets the settings passed and returns different settings.
   * @return An instance of the provider initialized with new settings.
   */
  override def withSettings(f: (Settings) => Settings) = {
    new CustomSteamProvider(httpLayer, service.withSettings(f), f(settings), steamUserDAO)
  }
}

/**
 * The profile parser for the common social profile.
 */
class CustomSteamProfileParser(steamUserDAO: SteamUserDAO) extends SocialProfileParser[Unit, ServiceProfile, OpenIDInfo] {

  /**
   * Parses the social profile.
   *
   * @param authInfo The auth info received from the provider.
   * @return The social profile from given result.
   */
  override def parse(data: Unit, authInfo: OpenIDInfo) = Future.successful {
    println("PaRsInG!1!1")
    steamUserDAO.processSummaries(steamUserDAO.getUserSummaries(List(extractId(authInfo.id)))).head
  }
}

