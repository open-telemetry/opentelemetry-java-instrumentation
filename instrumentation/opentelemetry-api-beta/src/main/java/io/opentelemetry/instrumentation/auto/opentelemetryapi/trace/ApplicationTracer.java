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

package io.opentelemetry.instrumentation.auto.opentelemetryapi.trace;

import application.io.grpc.Context;
import application.io.opentelemetry.context.Scope;
import application.io.opentelemetry.trace.Span;
import application.io.opentelemetry.trace.Tracer;
import io.opentelemetry.instrumentation.auto.api.ContextStore;

class ApplicationTracer implements Tracer {

  private final io.opentelemetry.trace.Tracer agentTracer;
  private final ContextStore<Context, io.grpc.Context> contextStore;

  ApplicationTracer(
      io.opentelemetry.trace.Tracer agentTracer,
      ContextStore<Context, io.grpc.Context> contextStore) {
    this.agentTracer = agentTracer;
    this.contextStore = contextStore;
  }

  @Override
  public Span getCurrentSpan() {
    return Bridging.toApplication(agentTracer.getCurrentSpan());
  }

  @Override
  public Scope withSpan(Span applicationSpan) {
    return TracingContextUtils.currentContextWith(applicationSpan);
  }

  @Override
  public Span.Builder spanBuilder(String spanName) {
    return new ApplicationSpan.Builder(agentTracer.spanBuilder(spanName), contextStore);
  }
}
