/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_5;

import io.grpc.Metadata;
import io.opentelemetry.context.propagation.TextMapGetter;

final class GrpcExtractAdapter implements TextMapGetter<Metadata> {

  static final GrpcExtractAdapter GETTER = new GrpcExtractAdapter();

  @Override
  public Iterable<String> keys(Metadata metadata) {
    return metadata.keys();
  }

  @Override
  public String get(Metadata carrier, String key) {
    return carrier.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));
  }
}
