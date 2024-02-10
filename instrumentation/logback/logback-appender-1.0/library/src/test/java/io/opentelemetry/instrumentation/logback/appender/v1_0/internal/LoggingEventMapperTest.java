/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0.internal;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LoggingEventMapperTest {

  @Test
  void testDefault() {
    // given
    LoggingEventMapper mapper = LoggingEventMapper.builder().build();
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
  void testSome() {
    // given
    LoggingEventMapper mapper =
        LoggingEventMapper.builder().setCaptureMdcAttributes(singletonList("key2")).build();
    Map<String, String> contextData = new HashMap<>();
    contextData.put("key1", "value1");
    contextData.put("key2", "value2");
    AttributesBuilder attributes = Attributes.builder();

    // when
    mapper.captureMdcAttributes(attributes, contextData);

    // then
    assertThat(attributes.build()).containsOnly(entry(AttributeKey.stringKey("key2"), "value2"));
  }

  @Test
  void testAll() {
    // given
    LoggingEventMapper mapper =
        LoggingEventMapper.builder().setCaptureMdcAttributes(singletonList("*")).build();
    Map<String, String> contextData = new HashMap<>();
    contextData.put("key1", "value1");
    contextData.put("key2", "value2");
    AttributesBuilder attributes = Attributes.builder();

    // when
    mapper.captureMdcAttributes(attributes, contextData);

    // then
    assertThat(attributes.build())
        .containsOnly(
            entry(AttributeKey.stringKey("key1"), "value1"),
            entry(AttributeKey.stringKey("key2"), "value2"));
  }
}
