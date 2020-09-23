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

package io.opentelemetry.instrumentation.auto.grpc.server;

import static io.opentelemetry.trace.Span.Kind.SERVER;

import io.grpc.Metadata;
import io.grpc.Status;
import io.opentelemetry.context.propagation.TextMapPropagator.Getter;
import io.opentelemetry.instrumentation.api.tracer.RpcServerTracer;
import io.opentelemetry.instrumentation.auto.grpc.common.GrpcHelper;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Builder;
import io.opentelemetry.trace.attributes.SemanticAttributes;

public class GrpcServerTracer extends RpcServerTracer<Metadata> {
  public static final GrpcServerTracer TRACER = new GrpcServerTracer();

  public Span startSpan(String name, Metadata headers) {
    Builder spanBuilder =
        tracer.spanBuilder(name).setSpanKind(SERVER).setParent(extract(headers, getGetter()));
    SemanticAttributes.RPC_SYSTEM.set(spanBuilder, "grpc");
    return spanBuilder.startSpan();
  }

  public void setStatus(Span span, Status status) {
    span.setStatus(GrpcHelper.statusFromGrpcStatus(status));
    if (status.getCause() != null) {
      addThrowable(span, status.getCause());
    }
  }

  @Override
  protected void onError(Span span, Throwable throwable) {
    Status grpcStatus = Status.fromThrowable(throwable);
    super.onError(span, grpcStatus.getCause());
    span.setStatus(GrpcHelper.statusFromGrpcStatus(grpcStatus));
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.grpc-1.5";
  }

  @Override
  protected Getter<Metadata> getGetter() {
    return GrpcExtractAdapter.GETTER;
  }
}
