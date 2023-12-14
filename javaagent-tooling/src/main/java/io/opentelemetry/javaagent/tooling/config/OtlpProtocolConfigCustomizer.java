/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import java.util.Collections;
import java.util.Map;

public class OtlpProtocolConfigCustomizer {

  private static final String HTTP_PROTOBUF = "http/protobuf";
  private static final String OTEL_EXPORTER_OTLP_PROTOCOL = "otel.exporter.otlp.protocol";

  public Map<String, String> getProperties() {
    return Collections.singletonMap(OTEL_EXPORTER_OTLP_PROTOCOL, HTTP_PROTOBUF);
  }
}
