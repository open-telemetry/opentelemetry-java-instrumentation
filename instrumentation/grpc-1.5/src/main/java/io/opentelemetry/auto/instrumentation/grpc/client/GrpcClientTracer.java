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
package io.opentelemetry.auto.instrumentation.grpc.client;

import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.opentelemetry.auto.instrumentation.grpc.common.GrpcHelper;
import io.opentelemetry.auto.semantic.client.ClientSemanticTracer;
import io.opentelemetry.trace.Span;
import java.net.InetSocketAddress;

public class GrpcClientTracer extends
    ClientSemanticTracer<GrpcClientSpan, MethodDescriptor, Status> {
  public static final GrpcClientTracer TRACER = new GrpcClientTracer();

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.grpc-1.5";
  }

  @Override
  protected String getVersion() {
    return null;
  }

  @Override
  protected String getSpanName(MethodDescriptor methodDescriptor) {
    return methodDescriptor.getFullMethodName();
  }

  @Override
  protected GrpcClientSpan wrapSpan(Span span) {
    return new GrpcClientSpan(span);
  }

  public final GrpcClientSpan startSpan(
      final MethodDescriptor method, final InetSocketAddress peerAddress) {
    GrpcClientSpan span = startSpan(method);
    GrpcHelper.prepareSpan(span, method.getFullMethodName(), peerAddress, false);
    return span;
  }
}
