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

import com.mohiva.play.silhouette.impl.providers.SocialProfileBuilder
import models.ServiceProfile

/**
 * The profile builder for the common social profile.
 */
trait ServiceProfileBuilder {
  self: SocialProfileBuilder =>

  /**
   * The type of the profile a profile builder is responsible for.
   */
  type Profile = ServiceProfile
}
