/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.grpc.Metadata;
import io.opentelemetry.context.propagation.TextMapSetter;
import javax.annotation.Nullable;

enum MetadataSetter implements TextMapSetter<Metadata> {
  INSTANCE;

  @Override
  public void set(@Nullable Metadata carrier, String key, String value) {
    if (carrier == null) {
      return;
    }
    Metadata.Key<String> metadataKey = Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER);
    carrier.removeAll(metadataKey);
    carrier.put(metadataKey, value);
  }
}
