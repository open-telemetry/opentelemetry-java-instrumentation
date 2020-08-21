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

package io.opentelemetry.instrumentation.auto.armeria.v1_0

import com.linecorp.armeria.client.WebClientBuilder
import com.linecorp.armeria.server.ServerBuilder
import io.opentelemetry.auto.test.AgentTestTrait
import io.opentelemetry.instrumentation.armeria.v1_0.AbstractArmeriaTest
import io.opentelemetry.instrumentation.armeria.v1_0.client.OpenTelemetryClient
import io.opentelemetry.instrumentation.armeria.v1_0.server.OpenTelemetryService

class ArmeriaNoDuplicateInstrumentationTest extends AbstractArmeriaTest implements AgentTestTrait {
  @Override
  ServerBuilder configureServer(ServerBuilder sb) {
    return sb.decorator(OpenTelemetryService.newDecorator())
  }

  @Override
  WebClientBuilder configureClient(WebClientBuilder clientBuilder) {
    return clientBuilder.decorator(OpenTelemetryClient.newDecorator())
  }

  def childSetupSpec() {
    backend.before()
    frontend.before()
  }

  def childCleanupSpec() {
    backend.after()
    frontend.after()
  }

  // Because our unit tests include io.grpc.Context in the bootstrap classloader, there does not
  // seem to be a simple way of testing this yet.
  @Override
  boolean supportsContext() {
    return false
  }
}
