/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.http;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import java.util.HashMap;
import java.util.Map;

@AutoService(AutoConfigurationCustomizerProvider.class)
public class CapturedHttpHeadersTestConfigSupplier implements AutoConfigurationCustomizerProvider {

  @Override
  public void customize(AutoConfigurationCustomizer autoConfiguration) {
    autoConfiguration.addPropertiesSupplier(
        CapturedHttpHeadersTestConfigSupplier::getTestProperties);
  }

  private static Map<String, String> getTestProperties() {
    Map<String, String> testConfig = new HashMap<>();
    testConfig.put("otel.instrumentation.http.capture-headers.client.request", "X-Test-Request");
    testConfig.put("otel.instrumentation.http.capture-headers.client.response", "X-Test-Response");
    testConfig.put("otel.instrumentation.http.capture-headers.server.request", "X-Test-Request");
    testConfig.put("otel.instrumentation.http.capture-headers.server.response", "X-Test-Response");
    return testConfig;
  }
}
