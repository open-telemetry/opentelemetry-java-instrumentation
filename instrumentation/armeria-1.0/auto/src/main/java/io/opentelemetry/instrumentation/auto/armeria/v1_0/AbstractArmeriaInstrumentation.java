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

package io.opentelemetry.auto.instrumentation.armeria.v1_0;

import io.opentelemetry.auto.tooling.Instrumenter;

public abstract class AbstractArmeriaInstrumentation extends Instrumenter.Default {

  private static final String INSTRUMENTATION_NAME = "armeria";

  public AbstractArmeriaInstrumentation() {
    super(INSTRUMENTATION_NAME);
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.instrumentation.armeria.v1_0.ArmeriaDecorators",
      "io.opentelemetry.instrumentation.armeria.v1_0.client.ArmeriaClientTracer",
      "io.opentelemetry.instrumentation.armeria.v1_0.client.ArmeriaClientTracer$ArmeriaSetter",
      "io.opentelemetry.instrumentation.armeria.v1_0.client.OpenTelemetryClient",
      "io.opentelemetry.instrumentation.armeria.v1_0.client.OpenTelemetryClient$Decorator",
      // Corresponds to lambda when calling .thenAccept(log -> ...
      "io.opentelemetry.instrumentation.armeria.v1_0.client.OpenTelemetryClient$1",
      "io.opentelemetry.instrumentation.armeria.v1_0.server.ArmeriaServerTracer",
      "io.opentelemetry.instrumentation.armeria.v1_0.server.ArmeriaServerTracer$ArmeriaGetter",
      "io.opentelemetry.instrumentation.armeria.v1_0.server.OpenTelemetryService",
      "io.opentelemetry.instrumentation.armeria.v1_0.server.OpenTelemetryService$Decorator",
      // Corresponds to lambda when calling .thenAccept(log -> ...
      "io.opentelemetry.instrumentation.armeria.v1_0.server.OpenTelemetryService$1",
    };
  }
}
