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
package io.opentelemetry.auto.instrumentation.grpc.server;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.Status;
import io.opentelemetry.auto.bootstrap.instrumentation.api.Pair;
import io.opentelemetry.auto.semantic.server.ServerSemanticTracer;
import io.opentelemetry.trace.Span;

import static io.opentelemetry.auto.bootstrap.instrumentation.decorator.BaseDecorator.extract;

public class GrpcServerTracer
    extends ServerSemanticTracer<GrpcServerSpan, Pair<ServerCall, Metadata>, Status> {
  public static final GrpcServerTracer TRACER = new GrpcServerTracer();

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.grpc-1.5";
  }

  @Override
  protected String getVersion() {
    return null;
  }

  @Override
  protected String getSpanName(Pair<ServerCall, Metadata> request) {
    return request.getLeft().getMethodDescriptor().getFullMethodName();
  }

  @Override
  protected GrpcServerSpan wrapSpan(Span span) {
    return new GrpcServerSpan(span);
  }

  @Override
  protected Span.Builder buildSpan(
      final Pair<ServerCall, Metadata> request, final Span.Builder spanBuilder) {
    spanBuilder.setParent(extract(request.getRight(), GrpcExtractAdapter.GETTER));
    return super.buildSpan(request, spanBuilder);
  }
}
