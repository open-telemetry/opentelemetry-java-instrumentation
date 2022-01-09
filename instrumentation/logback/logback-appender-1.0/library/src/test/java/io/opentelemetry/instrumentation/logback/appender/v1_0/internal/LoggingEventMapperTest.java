/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0.internal;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.entry;

import ch.qos.logback.classic.Level;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class LoggingEventMapperTest {

  @Test
  public void testDefault() {
    // given
    LoggingEventMapper mapper = new LoggingEventMapper(Level.ALL, emptyList());
    Map<String, String> contextData = new HashMap<>();
    contextData.put("key1", "value1");
    contextData.put("key2", "value2");
    AttributesBuilder attributes = Attributes.builder();

    // when
    mapper.captureMdcAttributes(attributes, contextData);

    // then
    assertThat(attributes.build()).isEmpty();
  }

  @Test
  public void testSome() {
    // given
    LoggingEventMapper mapper = new LoggingEventMapper(Level.ALL, singletonList("key2"));
    Map<String, String> contextData = new HashMap<>();
    contextData.put("key1", "value1");
    contextData.put("key2", "value2");
    AttributesBuilder attributes = Attributes.builder();

    // when
    mapper.captureMdcAttributes(attributes, contextData);

    // then
    assertThat(attributes.build())
        .containsOnly(entry(AttributeKey.stringKey("logback.mdc.key2"), "value2"));
  }

  @Test
  public void testWildcard() {
    // given
    LoggingEventMapper mapper = new LoggingEventMapper(Level.ALL, singletonList("*"));
    Map<String, String> contextData = new HashMap<>();
    contextData.put("key1", "value1");
    contextData.put("key2", "value2");
    AttributesBuilder attributes = Attributes.builder();

    // when
    mapper.captureMdcAttributes(attributes, contextData);

    // then
    assertThat(attributes.build())
        .containsOnly(
            entry(AttributeKey.stringKey("logback.mdc.key1"), "value1"),
            entry(AttributeKey.stringKey("logback.mdc.key2"), "value2"));
  }
}
