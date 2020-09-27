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

package io.opentelemetry.instrumentation.auto.grpc.client;

import static io.opentelemetry.trace.Span.Kind.CLIENT;

import io.grpc.Status;
import io.opentelemetry.instrumentation.api.tracer.RpcClientTracer;
import io.opentelemetry.instrumentation.auto.grpc.common.GrpcHelper;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Builder;
import io.opentelemetry.trace.attributes.SemanticAttributes;

public class GrpcClientTracer extends RpcClientTracer {
  public static final GrpcClientTracer TRACER = new GrpcClientTracer();

  public Span startSpan(String name) {
    Builder spanBuilder = tracer.spanBuilder(name).setSpanKind(CLIENT);
    spanBuilder.setAttribute(SemanticAttributes.RPC_SYSTEM, "grpc");
    return spanBuilder.startSpan();
  }

  public void endSpan(Span span, Status status) {
    span.setStatus(GrpcHelper.statusFromGrpcStatus(status));
    end(span);
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
}
