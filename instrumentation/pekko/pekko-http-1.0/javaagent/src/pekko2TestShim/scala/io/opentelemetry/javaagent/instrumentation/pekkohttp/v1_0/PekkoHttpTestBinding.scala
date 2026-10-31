/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.Http.{IncomingConnection, ServerBinding}
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.stream.scaladsl.Source

import scala.concurrent.Future

// pekko-http 2.x removed the Http().bindAndHandle* methods, the tests bind through this object so
// that they compile against both lines, this pekko-http 2.x variant binds with newServerAt
object PekkoHttpTestBinding {

  def bindRoute(route: Route, host: String, port: Int)(implicit
      system: ActorSystem
  ): Future[ServerBinding] =
    Http().newServerAt(host, port).bindFlow(Route.toFlow(route))

  def bindSync(
      handler: HttpRequest => HttpResponse,
      host: String,
      port: Int
  )(implicit system: ActorSystem): Future[ServerBinding] =
    Http().newServerAt(host, port).bindSync(handler)

  def bindAsync(
      handler: HttpRequest => Future[HttpResponse],
      host: String,
      port: Int
  )(implicit system: ActorSystem): Future[ServerBinding] =
    Http().newServerAt(host, port).bind(handler)

  def connectionSource(host: String, port: Int)(implicit
      system: ActorSystem
  ): Source[IncomingConnection, Future[ServerBinding]] =
    Http().newServerAt(host, port).connectionSource()
}
