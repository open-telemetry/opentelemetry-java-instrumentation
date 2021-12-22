/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_16.internal;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import org.junit.Test;

public class LogEventMapperTest {

  @Test
  public void testDefault() {
    // given
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(ContextDataAccessorImpl.INSTANCE, false, emptyList());
    Map<String, String> contextData = new HashMap<>();
    contextData.put("key1", "value1");
    contextData.put("key2", "value2");
    AttributesBuilder attributes = Attributes.builder();

    // when
    mapper.captureContextDataAttributes(attributes, contextData);

    // then
    assertThat(attributes.build()).isEmpty();
  }

  @Test
  public void testSome() {
    // given
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(ContextDataAccessorImpl.INSTANCE, false, singletonList("key2"));
    Map<String, String> contextData = new HashMap<>();
    contextData.put("key1", "value1");
    contextData.put("key2", "value2");
    AttributesBuilder attributes = Attributes.builder();

    // when
    mapper.captureContextDataAttributes(attributes, contextData);

    // then
    assertThat(attributes.build())
        .containsOnly(entry(AttributeKey.stringKey("log4j.context_data.key2"), "value2"));
  }

  @Test
  public void testAll() {
    // given
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(ContextDataAccessorImpl.INSTANCE, false, singletonList("*"));
    Map<String, String> contextData = new HashMap<>();
    contextData.put("key1", "value1");
    contextData.put("key2", "value2");
    AttributesBuilder attributes = Attributes.builder();

    // when
    mapper.captureContextDataAttributes(attributes, contextData);

    // then
    assertThat(attributes.build())
        .containsOnly(
            entry(AttributeKey.stringKey("log4j.context_data.key1"), "value1"),
            entry(AttributeKey.stringKey("log4j.context_data.key2"), "value2"));
  }

  private enum ContextDataAccessorImpl implements ContextDataAccessor<Map<String, String>> {
    INSTANCE;

    @Override
    @Nullable
    public Object getValue(Map<String, String> contextData, String key) {
      return contextData.get(key);
    }

    @Override
    public void forEach(Map<String, String> contextData, BiConsumer<String, Object> action) {
      contextData.forEach(action);
    }
  }
}
