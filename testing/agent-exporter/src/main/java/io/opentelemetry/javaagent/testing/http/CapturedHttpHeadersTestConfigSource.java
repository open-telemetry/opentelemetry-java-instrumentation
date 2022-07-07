/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.http;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.config.ConfigPropertySource;
import java.util.HashMap;
import java.util.Map;

@AutoService(ConfigPropertySource.class)
public class CapturedHttpHeadersTestConfigSource implements ConfigPropertySource {

  @Override
  public Map<String, String> getProperties() {
    Map<String, String> testConfig = new HashMap<>();
    testConfig.put("otel.instrumentation.http.capture-headers.client.request", "X-Test-Request");
    testConfig.put("otel.instrumentation.http.capture-headers.client.response", "X-Test-Response");
    testConfig.put("otel.instrumentation.http.capture-headers.server.request", "X-Test-Request");
    testConfig.put("otel.instrumentation.http.capture-headers.server.response", "X-Test-Response");
    return testConfig;
  }
}
