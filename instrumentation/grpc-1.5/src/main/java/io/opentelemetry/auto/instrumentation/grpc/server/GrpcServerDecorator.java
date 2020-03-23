/*
 * Copyright 2020, OpenTelemetry Authors
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
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

public class GrpcServerDecorator extends ServerDecorator {
  public static final GrpcServerDecorator DECORATE = new GrpcServerDecorator();
  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.grpc-1.5");

  @Override
  protected String getComponentName() {
    return "grpc-server";
  }

  public Span onClose(final Span span, final Status status) {

    span.setAttribute("status.code", status.getCode().name());
    span.setAttribute("status.description", status.getDescription());
    onError(span, status.getCause());
    if (!status.isOk()) {
      span.setStatus(io.opentelemetry.trace.Status.UNKNOWN);
    }
    return span;
  }
}
