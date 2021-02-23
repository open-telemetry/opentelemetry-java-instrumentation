/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_6;

import io.opentelemetry.instrumentation.api.config.Config;

public final class CouchbaseConfig {

  public static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      Config.get()
          .getBooleanProperty("otel.instrumentation.couchbase.experimental-span-attributes", false);

  private CouchbaseConfig() {}
}
