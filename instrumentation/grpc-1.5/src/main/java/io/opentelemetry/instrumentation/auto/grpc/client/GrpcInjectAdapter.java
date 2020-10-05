/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.grpc.client;

import io.grpc.Metadata;
import io.opentelemetry.context.propagation.TextMapPropagator;

public final class GrpcInjectAdapter implements TextMapPropagator.Setter<Metadata> {

  public static final GrpcInjectAdapter SETTER = new GrpcInjectAdapter();

  @Override
  public void set(Metadata carrier, String key, String value) {
    carrier.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value);
  }
}
