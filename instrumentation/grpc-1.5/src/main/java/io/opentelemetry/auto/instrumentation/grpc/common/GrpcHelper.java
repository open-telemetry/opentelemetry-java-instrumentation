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

package io.opentelemetry.auto.instrumentation.grpc.common;

import io.grpc.Status.Code;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.Status.CanonicalCode;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class GrpcHelper {
  private static final Map<Code, CanonicalCode> CODE_MAP;

  static {
    EnumMap<Code, CanonicalCode> codeMap = new EnumMap<>(Code.class);
    codeMap.put(Code.OK, CanonicalCode.OK);
    codeMap.put(Code.CANCELLED, CanonicalCode.CANCELLED);
    codeMap.put(Code.INVALID_ARGUMENT, CanonicalCode.INVALID_ARGUMENT);
    codeMap.put(Code.DEADLINE_EXCEEDED, CanonicalCode.DEADLINE_EXCEEDED);
    codeMap.put(Code.NOT_FOUND, CanonicalCode.NOT_FOUND);
    codeMap.put(Code.ALREADY_EXISTS, CanonicalCode.ALREADY_EXISTS);
    codeMap.put(Code.PERMISSION_DENIED, CanonicalCode.PERMISSION_DENIED);
    codeMap.put(Code.RESOURCE_EXHAUSTED, CanonicalCode.RESOURCE_EXHAUSTED);
    codeMap.put(Code.FAILED_PRECONDITION, CanonicalCode.FAILED_PRECONDITION);
    codeMap.put(Code.ABORTED, CanonicalCode.ABORTED);
    codeMap.put(Code.OUT_OF_RANGE, CanonicalCode.OUT_OF_RANGE);
    codeMap.put(Code.UNIMPLEMENTED, CanonicalCode.UNIMPLEMENTED);
    codeMap.put(Code.INTERNAL, CanonicalCode.INTERNAL);
    codeMap.put(Code.UNAVAILABLE, CanonicalCode.UNAVAILABLE);
    codeMap.put(Code.DATA_LOSS, CanonicalCode.DATA_LOSS);
    codeMap.put(Code.UNAUTHENTICATED, CanonicalCode.UNAUTHENTICATED);
    codeMap.put(Code.UNKNOWN, CanonicalCode.UNKNOWN);
    CODE_MAP = Collections.unmodifiableMap(codeMap);
  }

  public static void prepareSpan(
      final Span span,
      final String methodName,
      final InetSocketAddress peerAddress,
      final boolean server) {
    String serviceName =
        "(unknown)"; // Spec says it's mandatory, so populate even if we couldn't determine it.
    final int slash = methodName.indexOf('/');
    if (slash != -1) {
      final String fullServiceName = methodName.substring(0, slash);
      final int dot = fullServiceName.lastIndexOf('.');
      if (dot != -1) {
        serviceName = fullServiceName.substring(dot + 1);
      }
    }
    span.setAttribute(MoreTags.RPC_SERVICE, serviceName);
    if (peerAddress != null) {
      span.setAttribute(MoreTags.NET_PEER_PORT, peerAddress.getPort());
      if (server) {
        span.setAttribute(MoreTags.NET_PEER_IP, peerAddress.getAddress().getHostAddress());
      } else {
        span.setAttribute(MoreTags.NET_PEER_NAME, peerAddress.getHostName());
      }
    } else {
      // The spec says these fields must be populated, so put some values in even if we don't have
      // an address recorded.
      span.setAttribute(MoreTags.NET_PEER_PORT, 0);
      span.setAttribute(MoreTags.NET_PEER_NAME, "(unknown)");
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
    return code != null ? code : CanonicalCode.UNKNOWN;
  }

  private GrpcHelper() {}
}
