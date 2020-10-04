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

package io.opentelemetry.instrumentation.auto.grpc.common;

import io.grpc.Status.Code;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.Status.CanonicalCode;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class GrpcHelper {
  private static final Map<Code, CanonicalCode> CODE_MAP;

  static {
    EnumMap<Code, CanonicalCode> codeMap = new EnumMap<>(Code.class);
    codeMap.put(Code.CANCELLED, CanonicalCode.ERROR);
    codeMap.put(Code.INVALID_ARGUMENT, CanonicalCode.ERROR);
    codeMap.put(Code.DEADLINE_EXCEEDED, CanonicalCode.ERROR);
    codeMap.put(Code.NOT_FOUND, CanonicalCode.ERROR);
    codeMap.put(Code.ALREADY_EXISTS, CanonicalCode.ERROR);
    codeMap.put(Code.PERMISSION_DENIED, CanonicalCode.ERROR);
    codeMap.put(Code.RESOURCE_EXHAUSTED, CanonicalCode.ERROR);
    codeMap.put(Code.FAILED_PRECONDITION, CanonicalCode.ERROR);
    codeMap.put(Code.ABORTED, CanonicalCode.ERROR);
    codeMap.put(Code.OUT_OF_RANGE, CanonicalCode.ERROR);
    codeMap.put(Code.UNIMPLEMENTED, CanonicalCode.ERROR);
    codeMap.put(Code.INTERNAL, CanonicalCode.ERROR);
    codeMap.put(Code.UNAVAILABLE, CanonicalCode.ERROR);
    codeMap.put(Code.DATA_LOSS, CanonicalCode.ERROR);
    codeMap.put(Code.UNAUTHENTICATED, CanonicalCode.ERROR);
    codeMap.put(Code.UNKNOWN, CanonicalCode.ERROR);
    CODE_MAP = Collections.unmodifiableMap(codeMap);
  }

  public static void prepareSpan(
      Span span, String fullMethodName, InetSocketAddress peerAddress, boolean server) {

    int slash = fullMethodName.indexOf('/');
    String serviceName = slash == -1 ? fullMethodName : fullMethodName.substring(0, slash);
    String methodName = slash == -1 ? null : fullMethodName.substring(slash + 1);

    span.setAttribute(SemanticAttributes.RPC_SERVICE, serviceName);
    span.setAttribute(SemanticAttributes.RPC_METHOD, methodName);

    if (peerAddress != null) {
      span.setAttribute(SemanticAttributes.NET_PEER_PORT, (long) peerAddress.getPort());
      if (server) {
        span.setAttribute(
            SemanticAttributes.NET_PEER_IP, peerAddress.getAddress().getHostAddress());
      } else {
        NetPeerUtils.setNetPeer(span, peerAddress.getHostName(), null);
      }
    } else {
      // The spec says these fields must be populated, so put some values in even if we don't have
      // an address recorded.
      span.setAttribute(SemanticAttributes.NET_PEER_PORT, 0L);
      NetPeerUtils.setNetPeer(span, "(unknown)", null);
    }
  }

  public static Status statusFromGrpcStatus(io.grpc.Status grpcStatus) {
    Status status = codeFromGrpcCode(grpcStatus.getCode()).toStatus();
    if (grpcStatus.getDescription() != null) {
      status = status.withDescription(grpcStatus.getDescription());
    }
    return status;
  }

  private static CanonicalCode codeFromGrpcCode(Code grpcCode) {
    CanonicalCode code = CODE_MAP.get(grpcCode);
    return code != null ? code : CanonicalCode.UNSET;
  }

  private GrpcHelper() {}
}
