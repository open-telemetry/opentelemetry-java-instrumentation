/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import io.grpc.Metadata;
import io.grpc.Status;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributesGetter;
import java.util.List;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;

enum GrpcRpcAttributesGetter implements RpcAttributesGetter<GrpcRequest, Status> {
  INSTANCE;

  @Override
  public String getSystem(GrpcRequest request) {
    return "grpc";
  }

  @Override
  @Nullable
  public String getService(GrpcRequest request) {
    String fullMethodName = request.getFullMethodName();
    int slashIndex = fullMethodName.lastIndexOf('/');
    if (slashIndex == -1) {
      return null;
    }
    return fullMethodName.substring(0, slashIndex);
  }

  @Deprecated
  @Override
  @Nullable
  public String getMethod(GrpcRequest request) {
    String fullMethodName = request.getFullMethodName();
    int slashIndex = fullMethodName.lastIndexOf('/');
    if (slashIndex == -1) {
      return null;
    }
    return fullMethodName.substring(slashIndex + 1);
  }

  @Override
  @Nullable
  public String getRpcMethod(GrpcRequest request) {
    return request.getFullMethodName();
  }

  @Override
  @Nullable
  public Long getRequestSize(GrpcRequest request) {
    return request.getRequestSize();
  }

  @Override
  @Nullable
  public Long getResponseSize(GrpcRequest request) {
    return request.getResponseSize();
  }

  @Override
  @Nullable
  public String getRpcMethodOriginal(GrpcRequest request) {
    return request.getOriginalFullMethodName();
  }

  List<String> metadataValue(GrpcRequest request, String key) {
    if (request.getMetadata() == null) {
      return emptyList();
    }

    if (key == null || key.isEmpty()) {
      return emptyList();
    }

    Iterable<String> values =
        request.getMetadata().getAll(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));

    if (values == null) {
      return emptyList();
    }

    return StreamSupport.stream(values.spliterator(), false).collect(toList());
  }
}
