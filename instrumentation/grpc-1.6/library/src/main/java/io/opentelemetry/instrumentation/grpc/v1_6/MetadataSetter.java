/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.grpc.Metadata;
import io.opentelemetry.context.propagation.TextMapSetter;

enum MetadataSetter implements TextMapSetter<Metadata> {
  INSTANCE;

  @Override
  public void set(Metadata carrier, String key, String value) {
    carrier.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value);
  }
}
