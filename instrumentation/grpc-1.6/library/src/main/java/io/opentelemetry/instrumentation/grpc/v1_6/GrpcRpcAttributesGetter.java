/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.grpc.Metadata;
import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcAttributesGetter;
import javax.annotation.Nullable;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

enum GrpcRpcAttributesGetter implements RpcAttributesGetter<GrpcRequest> {
  INSTANCE;

  @Override
  public String system(GrpcRequest request) {
    return "grpc";
  }

  @Override
  @Nullable
  public String service(GrpcRequest request) {
    String fullMethodName = request.getMethod().getFullMethodName();
    int slashIndex = fullMethodName.lastIndexOf('/');
    if (slashIndex == -1) {
      return null;
    }
    return fullMethodName.substring(0, slashIndex);
  }

  @Override
  @Nullable
  public String method(GrpcRequest request) {
    String fullMethodName = request.getMethod().getFullMethodName();
    int slashIndex = fullMethodName.lastIndexOf('/');
    if (slashIndex == -1) {
      return null;
    }
    return fullMethodName.substring(slashIndex + 1);
  }

  @Nullable
  public List<String> metadataValue(GrpcRequest request, String key) {
    if (request.getMetadata() == null) {
      return null;
    }

    if (key == null || key.isEmpty()) {
      return null;
    }

    Iterable<String> values = request.getMetadata().getAll(
        Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER)
    );

    if (values == null) {
      return null;
    }

    return StreamSupport.stream(values.spliterator(), false)
        .collect(Collectors.toList());
  }
}
