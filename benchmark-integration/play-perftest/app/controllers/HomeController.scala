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

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's work page which does busy wait to simulate some work
 */
class HomeController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {
  val TRACER: Tracer = OpenTelemetry.getTracerProvider.get("test")

  /**
   * Create an Action to perform busy wait
   */
  def doGet(workTimeMillis: Option[Long], error: Option[String]) = Action {
    implicit request: Request[AnyContent] =>
      error match {
        case Some(x) => throw new RuntimeException("some sync error")
        case None => {
          var workTime = workTimeMillis.getOrElse(0L)
          scheduleWork(workTime)
          Ok("Did " + workTime + "ms of work.")
        }
      }

  }

  private def scheduleWork(workTimeMillis: Long) {
    val span = tracer().spanBuilder("work").startSpan()
    val scope = tracer().withSpan(span)
    try {
      if (span != null) {
        span.setAttribute("work-time", workTimeMillis)
        span.setAttribute("info", "interesting stuff")
        span.setAttribute("additionalInfo", "interesting stuff")
      }
      if (workTimeMillis > 0) {
        Worker.doWork(workTimeMillis)
      }
    } finally {
      span.end()
      scope.close()
    }
  }
}
