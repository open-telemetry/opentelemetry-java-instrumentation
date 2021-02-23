package io.opentelemetry.javaagent.instrumentation.couchbase.v2_6;

import io.opentelemetry.instrumentation.api.config.Config;

public class CouchbaseConfig {
  public static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      Config.get()
          .getBooleanProperty("otel.instrumentation.couchbase.experimental-span-attributes", false);
}
