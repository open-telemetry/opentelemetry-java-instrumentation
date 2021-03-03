/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_5;

import io.grpc.Metadata;
import io.opentelemetry.context.propagation.TextMapSetter;

final class GrpcInjectAdapter implements TextMapSetter<Metadata> {

  static final GrpcInjectAdapter SETTER = new GrpcInjectAdapter();

  @Override
  public void set(Metadata carrier, String key, String value) {
    carrier.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value);
  }
}
