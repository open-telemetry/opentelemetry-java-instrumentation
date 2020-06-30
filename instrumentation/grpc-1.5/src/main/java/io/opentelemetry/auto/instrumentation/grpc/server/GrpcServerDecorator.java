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

import io.grpc.Status;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.ServerDecorator;
import io.opentelemetry.auto.instrumentation.grpc.common.GrpcHelper;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

public class GrpcServerDecorator extends ServerDecorator {
  public static final GrpcServerDecorator DECORATE = new GrpcServerDecorator();
  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.grpc-1.5");

  public Span onClose(final Span span, final Status status) {
    if (status.getCause() != null) {
      // We have a Status so only call super.onError to fill in common error tags.
      super.onError(span, status.getCause());
    }
    span.setStatus(GrpcHelper.statusFromGrpcStatus(status));
    return span;
  }

  @Override
  public Span onError(Span span, Throwable throwable) {
    assert span != null;
    if (throwable != null) {
      super.onError(span, throwable);
      span.setStatus(GrpcHelper.statusFromGrpcStatus(Status.fromThrowable(throwable)));
    }
    return span;
  }
}
