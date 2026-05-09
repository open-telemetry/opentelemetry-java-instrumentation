/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17.internal;

import io.nats.client.impl.Headers;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class NatsMessageWritableHeaders {

  public static Headers create(@Nullable Headers headers) {
    if (headers == null || headers.isReadOnly()) {
      return new Headers(headers);
    }

    return headers;
  }

  private NatsMessageWritableHeaders() {}
}
