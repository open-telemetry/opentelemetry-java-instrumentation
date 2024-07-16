/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.grpc.Metadata;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributesGetter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;

enum GrpcRpcAttributesGetter implements RpcAttributesGetter<GrpcRequest> {
  INSTANCE;

  @Override
  public String getSystem(GrpcRequest request) {
    return "grpc";
  }

  @Override
  @Nullable
  public String getService(GrpcRequest request) {
    String fullMethodName = request.getMethod().getFullMethodName();
    int slashIndex = fullMethodName.lastIndexOf('/');
    if (slashIndex == -1) {
      return null;
    }
    return fullMethodName.substring(0, slashIndex);
  }

  @Override
  @Nullable
  public String getMethod(GrpcRequest request) {
    String fullMethodName = request.getMethod().getFullMethodName();
    int slashIndex = fullMethodName.lastIndexOf('/');
    if (slashIndex == -1) {
      return null;
    }
    return fullMethodName.substring(slashIndex + 1);
  }

  @Override
  public int getClientRequestSize(GrpcRequest request) {
    return request.getClientRequestSize();
  }

  @Override
  public int getClientResponseSize(GrpcRequest request) {
    return request.getClientResponseSize();
  }

  @Override
  public int getServerRequestSize(GrpcRequest request) {
    return request.getServerRequestSize();
  }

  @Override
  public int getServerResponseSize(GrpcRequest request) {
    return request.getServerResponseSize();
  }


  List<String> metadataValue(GrpcRequest request, String key) {
    if (request.getMetadata() == null) {
      return Collections.emptyList();
    }

    if (key == null || key.isEmpty()) {
      return Collections.emptyList();
    }

    Iterable<String> values =
        request.getMetadata().getAll(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));

    if (values == null) {
      return Collections.emptyList();
    }

    return StreamSupport.stream(values.spliterator(), false).collect(Collectors.toList());
  }
}
