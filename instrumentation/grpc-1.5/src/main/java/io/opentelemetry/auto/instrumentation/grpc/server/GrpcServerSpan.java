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

import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.Status;
import io.opentelemetry.auto.bootstrap.instrumentation.api.Pair;
import io.opentelemetry.auto.instrumentation.grpc.common.GrpcHelper;
import io.opentelemetry.auto.semantic.server.ServerSemanticSpan;
import io.opentelemetry.common.AttributeValue;
import io.opentelemetry.trace.Span;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;

public class GrpcServerSpan
    extends ServerSemanticSpan<GrpcServerSpan, Pair<ServerCall, Metadata>, Status> {
  public GrpcServerSpan(Span delegate) {
    super(delegate);
  }

  @Override
  protected GrpcServerSpan onRequest(Pair<ServerCall, Metadata> request) {
    final SocketAddress addr =
        request.getLeft().getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
    final InetSocketAddress iAddr =
        addr instanceof InetSocketAddress ? (InetSocketAddress) addr : null;
    GrpcHelper.prepareSpan(
        this, request.getLeft().getMethodDescriptor().getFullMethodName(), iAddr, true);
    return this;
  }

  public GrpcServerSpan onMessage(int messageId) {
    final Map<String, AttributeValue> attributes = new HashMap<>();
    attributes.put("message.type", AttributeValue.stringAttributeValue("SENT"));
    attributes.put("message.id", AttributeValue.longAttributeValue(messageId));
    addEvent("message", attributes);
    return this;
  }

  @Override
  public GrpcServerSpan onResponse(Status status) {
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

  public GrpcServerSpan onError(Throwable throwable) {
    setStatus(io.opentelemetry.trace.Status.UNKNOWN);
    setThrowable(throwable);
    return this;
  }

  @Override
  protected GrpcServerSpan self() {
    return this;
  }
}
