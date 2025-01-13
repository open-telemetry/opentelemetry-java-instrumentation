/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import static java.util.Collections.emptyIterator;

import io.grpc.Metadata;
import io.opentelemetry.context.propagation.internal.ExtendedTextMapGetter;
import java.util.Iterator;
import javax.annotation.Nullable;

enum GrpcRequestGetter implements ExtendedTextMapGetter<GrpcRequest> {
  INSTANCE;

  @Override
  public Iterable<String> keys(GrpcRequest request) {
    return request.getMetadata().keys();
  }

  @Override
  @Nullable
  public String get(@Nullable GrpcRequest request, String key) {
    if (request == null) {
      return null;
    }
    return request.getMetadata().get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));
  }

  @Override
  public Iterator<String> getAll(@Nullable GrpcRequest request, String key) {
    if (request == null) {
      return emptyIterator();
    }

    Iterable<String> values =
        request.getMetadata().getAll(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));

    if (values == null) {
      return emptyIterator();
    }
    return values.iterator();
  }
}
