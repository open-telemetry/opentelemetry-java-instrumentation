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

import io.grpc.Status;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.instrumentation.auto.grpc.common.GrpcHelper;
import io.opentelemetry.instrumentation.api.decorator.ClientDecorator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

public class GrpcClientDecorator extends ClientDecorator {
  public static final GrpcClientDecorator DECORATE = new GrpcClientDecorator();
  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.grpc-1.5");

  public Span onClose(final Span span, final io.grpc.Status status) {
    onComplete(span, GrpcHelper.statusFromGrpcStatus(status), status.getCause());
    return span;
  }

  @Override
  public Span onError(Span span, Throwable throwable) {
    assert span != null;
    onClose(span, Status.fromThrowable(throwable));
    return span;
  }
}
