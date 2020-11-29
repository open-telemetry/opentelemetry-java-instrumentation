/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server

import groovy.lang.Closure
import play.api.mvc.Result

import scala.concurrent.Future

class ControllerClosureAdapter(response: Result) extends Closure[Result] {
  override def call(): Result = response
}

class BlockClosureAdapter(block: () => Result) extends Closure[Result] {
  override def call(): Result = block()
}

class AsyncControllerClosureAdapter(response: Future[Result]) extends Closure[Future[Result]] {
  override def call(): Future[Result] = response
}

class AsyncBlockClosureAdapter(block: () => Future[Result]) extends Closure[Future[Result]] {
  override def call(): Future[Result] = block()
}
