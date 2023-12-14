/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static java.util.logging.Level.WARNING;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

public class OtlpProtocolConfigCustomizer
    implements Function<ConfigProperties, Map<String, String>> {

  private static final String HTTP_PROTOBUF = "http/protobuf";
  private static final String OTEL_EXPORTER_OTLP_PROTOCOL = "otel.exporter.otlp.protocol";
  private static final Logger logger =
      Logger.getLogger(OtlpProtocolConfigCustomizer.class.getName());

  private final Map<String, String> properties = new HashMap<>();

  @SuppressWarnings("SystemOut")
  @Override
  public Map<String, String> apply(ConfigProperties config) {
    if (config.getString(OTEL_EXPORTER_OTLP_PROTOCOL) == null) {
      properties.put(OTEL_EXPORTER_OTLP_PROTOCOL, HTTP_PROTOBUF);
    } else {
      logger.log(
          WARNING,
          "otel.exporter.otlp.protocol is already set to {0}. Not overriding it with default value {1}.",
          new Object[] {config.getString(OTEL_EXPORTER_OTLP_PROTOCOL), HTTP_PROTOBUF});
    }
    return properties;
  }

  public Map<String, String> getProperties() {
    return Collections.unmodifiableMap(properties);
  }
}
