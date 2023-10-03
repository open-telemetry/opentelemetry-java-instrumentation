/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer

import scala.concurrent.ExecutionContext

trait PekkoImplicits {
  implicit val system: ActorSystem = ActorSystem("my-system")
  implicit val materializer: Materializer = Materializer.matFromSystem
  implicit val executionContext: ExecutionContext = system.dispatcher
}
