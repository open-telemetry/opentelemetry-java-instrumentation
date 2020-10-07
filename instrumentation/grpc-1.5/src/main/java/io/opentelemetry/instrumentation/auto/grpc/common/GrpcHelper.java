/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.grpc.common;

import io.grpc.Status.Code;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.StatusCanonicalCode;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class GrpcHelper {
  private static final Map<Code, StatusCanonicalCode> CODE_MAP;

  static {
    EnumMap<Code, StatusCanonicalCode> codeMap = new EnumMap<>(Code.class);
    codeMap.put(Code.CANCELLED, StatusCanonicalCode.ERROR);
    codeMap.put(Code.INVALID_ARGUMENT, StatusCanonicalCode.ERROR);
    codeMap.put(Code.DEADLINE_EXCEEDED, StatusCanonicalCode.ERROR);
    codeMap.put(Code.NOT_FOUND, StatusCanonicalCode.ERROR);
    codeMap.put(Code.ALREADY_EXISTS, StatusCanonicalCode.ERROR);
    codeMap.put(Code.PERMISSION_DENIED, StatusCanonicalCode.ERROR);
    codeMap.put(Code.RESOURCE_EXHAUSTED, StatusCanonicalCode.ERROR);
    codeMap.put(Code.FAILED_PRECONDITION, StatusCanonicalCode.ERROR);
    codeMap.put(Code.ABORTED, StatusCanonicalCode.ERROR);
    codeMap.put(Code.OUT_OF_RANGE, StatusCanonicalCode.ERROR);
    codeMap.put(Code.UNIMPLEMENTED, StatusCanonicalCode.ERROR);
    codeMap.put(Code.INTERNAL, StatusCanonicalCode.ERROR);
    codeMap.put(Code.UNAVAILABLE, StatusCanonicalCode.ERROR);
    codeMap.put(Code.DATA_LOSS, StatusCanonicalCode.ERROR);
    codeMap.put(Code.UNAUTHENTICATED, StatusCanonicalCode.ERROR);
    codeMap.put(Code.UNKNOWN, StatusCanonicalCode.ERROR);
    CODE_MAP = Collections.unmodifiableMap(codeMap);
  }

  public static void prepareSpan(
      Span span, String fullMethodName, InetSocketAddress peerAddress, boolean server) {

    int slash = fullMethodName.indexOf('/');
    String serviceName = slash == -1 ? fullMethodName : fullMethodName.substring(0, slash);
    String methodName = slash == -1 ? null : fullMethodName.substring(slash + 1);

    span.setAttribute(SemanticAttributes.RPC_SERVICE, serviceName);
    if (methodName != null) {
      span.setAttribute(SemanticAttributes.RPC_METHOD, methodName);
    }

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

  public static StatusCanonicalCode statusFromGrpcStatus(io.grpc.Status grpcStatus) {
    return codeFromGrpcCode(grpcStatus.getCode());
  }

  private static StatusCanonicalCode codeFromGrpcCode(Code grpcCode) {
    StatusCanonicalCode code = CODE_MAP.get(grpcCode);
    return code != null ? code : StatusCanonicalCode.UNSET;
  }

  private GrpcHelper() {}
}
