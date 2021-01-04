/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.spi.config.PropertySource;
import java.util.Collections;
import java.util.Map;

@AutoService(PropertySource.class)
public class AgentTestingExporterPropertySource implements PropertySource {
  @Override
  public Map<String, String> getProperties() {
    return Collections.singletonMap("otel.exporter", "");
  }
}
