package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import io.opentelemetry.instrumentation.api.config.Config;

public class JaxrsConfig {

  public static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      Config.get()
          .getBooleanProperty("otel.instrumentation.jaxrs.experimental-span-attributes", false);
}
