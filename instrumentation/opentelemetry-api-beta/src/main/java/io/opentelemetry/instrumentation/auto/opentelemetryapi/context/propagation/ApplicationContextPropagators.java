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

package io.opentelemetry.instrumentation.auto.opentelemetryapi.context.propagation;

import application.io.grpc.Context;
import application.io.opentelemetry.context.propagation.ContextPropagators;
import application.io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.instrumentation.auto.api.ContextStore;

public class ApplicationContextPropagators implements ContextPropagators {

  private final ApplicationTextMapPropagator applicationTextMapPropagator;

  public ApplicationContextPropagators(ContextStore<Context, io.grpc.Context> contextStore) {
    applicationTextMapPropagator =
        new ApplicationTextMapPropagator(
            OpenTelemetry.getPropagators().getTextMapPropagator(), contextStore);
  }

  @Override
  public TextMapPropagator getTextMapPropagator() {
    return applicationTextMapPropagator;
  }
}
