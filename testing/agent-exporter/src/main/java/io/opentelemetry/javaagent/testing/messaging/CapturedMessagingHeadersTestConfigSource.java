/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.messaging;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.config.ConfigPropertySource;
import java.util.HashMap;
import java.util.Map;

@AutoService(ConfigPropertySource.class)
public class CapturedMessagingHeadersTestConfigSource implements ConfigPropertySource {

  @Override
  public Map<String, String> getProperties() {
    Map<String, String> testConfig = new HashMap<>();
    testConfig.put(
        "otel.instrumentation.messaging.experimental.capture-headers",
        // most tests use "test-message-header", "test_message_header" is used for JMS2 because
        // '-' is not allowed in a JMS property name. JMS property name should be a valid java
        // identifier.
        "test-message-header, test-message-int-header, test_message_header, test_message_int_header");
    return testConfig;
  }
}
