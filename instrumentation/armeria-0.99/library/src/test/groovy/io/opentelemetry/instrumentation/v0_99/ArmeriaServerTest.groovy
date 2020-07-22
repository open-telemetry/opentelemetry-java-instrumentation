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

package io.opentelemetry.instrumentation.v0_99

import com.linecorp.armeria.server.ServerBuilder
import io.opentelemetry.auto.test.InstrumentationTestTrait
import io.opentelemetry.instrumentation.armeria.v0_99.server.OpenTelemetryService
import io.opentelemetry.instrumentation.armeria.v_0_99.AbstractArmeriaServerTest

class ArmeriaServerTest extends AbstractArmeriaServerTest implements InstrumentationTestTrait {
  @Override
  void configureServer(ServerBuilder sb) {
    sb.decorator(OpenTelemetryService.newDecorator())
  }
}
