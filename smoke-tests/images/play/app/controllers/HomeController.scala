/*
 * Copyright The OpenTelemetry Authors
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

package controllers

import javax.inject.Inject

import play.api.mvc._

/** Creates an `Action` to handle HTTP requests to the application's welcome greeting */
class HomeController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  /** Create an Action to return a greeting */
  def doGet(id: Option[Int]) = Action { implicit request: Request[AnyContent] =>
    val idVal = id.getOrElse(-1)
    if (idVal > 0) {
      Ok("Welcome %d.".format(idVal))
    } else {
      Ok("No ID.")
    }
  }
}
