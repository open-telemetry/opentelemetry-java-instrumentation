/*
 * Copyright 2020, OpenTelemetry Authors
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
