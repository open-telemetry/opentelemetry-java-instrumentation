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

/**
 * Provides peer service mapping configuration for HTTP client tests. Maps localhost addresses to
 * "test-peer-service" to enable automatic peer service testing.
 */
@AutoService(AutoConfigurationCustomizerProvider.class)
public class PeerServiceTestConfigSupplier implements AutoConfigurationCustomizerProvider {

  @Override
  public void customize(AutoConfigurationCustomizer autoConfiguration) {
    autoConfiguration.addPropertiesSupplier(PeerServiceTestConfigSupplier::getTestProperties);
  }

  private static Map<String, String> getTestProperties() {
    Map<String, String> testConfig = new HashMap<>();
    testConfig.put(
        "otel.instrumentation.common.peer-service-mapping",
        "127.0.0.1=test-peer-service,localhost=test-peer-service,192.0.2.1=test-peer-service");
    return testConfig;
  }
}
