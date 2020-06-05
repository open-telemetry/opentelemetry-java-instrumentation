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

import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.instrumentation.grpc.common.GrpcHelper;
import io.opentelemetry.auto.typed.client.ClientTypedSpan;
import io.opentelemetry.common.AttributeValue;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.TracingContextUtils;
import java.util.HashMap;
import java.util.Map;

public class GrpcClientSpan extends ClientTypedSpan<GrpcClientSpan, MethodDescriptor, Status> {
  public GrpcClientSpan(Span delegate) {
    super(delegate);
  }

  @Override
  protected GrpcClientSpan onRequest(MethodDescriptor s) {
    return this;
  }

  public GrpcClientSpan onRequest(Metadata metadata) {
    // this reference to io.grpc.Context will be shaded during the build
    // see instrumentation.gradle: "relocate OpenTelemetry API dependency usage"
    // (luckily the grpc instrumentation doesn't need to reference unshaded grpc Context, so we
    // don't need to worry about distinguishing them like in the opentelemetry-api
    // instrumentation)
    final Context context = TracingContextUtils.withSpan(this, Context.current());
    OpenTelemetry.getPropagators().getHttpTextFormat().inject(context, metadata, GrpcInjectAdapter.SETTER);
    return this;
  }

  public GrpcClientSpan onMessage(int messageId) {
    final Map<String, AttributeValue> attributes = new HashMap<>();
    attributes.put("message.type", AttributeValue.stringAttributeValue("SENT"));
    attributes.put("message.id", AttributeValue.longAttributeValue(messageId));
    addEvent("message", attributes);
    return this;
  }

  @Override
  protected GrpcClientSpan onResponse(Status status) {
    setAttribute("status.code", status.getCode().name());
    setAttribute("status.description", status.getDescription());

    if (status.getCause() != null) {
      setThrowable(status.getCause());
    }
    if (!status.isOk()) {
      setStatus(io.opentelemetry.trace.Status.UNKNOWN);
    }
    return this;
  }

  @Override
  protected GrpcClientSpan self() {
    return this;
  }
}
