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
package io.opentelemetry.auto.instrumentation.opentelemetryapi.context.propagation;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.ContextStore;
import unshaded.io.grpc.Context;
import unshaded.io.opentelemetry.context.propagation.ContextPropagators;
import unshaded.io.opentelemetry.context.propagation.HttpTextFormat;

public class UnshadedContextPropagators implements ContextPropagators {

  private final UnshadedHttpTextFormat unshadedHttpTextFormat;

  public UnshadedContextPropagators(final ContextStore<Context, io.grpc.Context> contextStore) {
    unshadedHttpTextFormat =
        new UnshadedHttpTextFormat(
            OpenTelemetry.getPropagators().getHttpTextFormat(), contextStore);
  }

  @Override
  public HttpTextFormat getHttpTextFormat() {
    return unshadedHttpTextFormat;
  }
}
