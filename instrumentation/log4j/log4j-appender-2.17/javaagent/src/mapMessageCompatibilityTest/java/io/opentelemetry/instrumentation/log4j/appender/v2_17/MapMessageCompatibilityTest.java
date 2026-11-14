/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.MapMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MapMessageCompatibilityTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final Logger logger = LogManager.getLogger("compatibility");

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"}) // MapMessage was not generic before Log4j 2.9.
  void capturesMessageWithSupportedApi() {
    Map<String, String> data = new HashMap<>();
    data.put("message", "body");
    data.put("otel.event.name", "event");
    data.put("keep-key", "value");
    data.put("keep-secret", "excluded");
    data.put("other", "excluded");
    data.put("", "excluded");
    MapMessage message = spy(new MapMessage(data));

    logger.error(message);

    testing.waitAndAssertLogRecords(
        log ->
            log.hasBody("body")
                .hasEventName("event")
                .hasAttributesSatisfyingExactly(equalTo(stringKey("keep-key"), "value")));
    if (Boolean.getBoolean("test.mapMessageUsesGetData")) {
      verify(message).getData();
    } else {
      verify(message, never()).getData();
    }
  }
}
