/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17.internal;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.logs.LogRecordBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import org.apache.logging.log4j.message.StringMapMessage;
import org.apache.logging.log4j.message.StructuredDataMessage;
import org.junit.jupiter.api.Test;

class LogEventMapperTest {

  @Test
  void testDefault() {
    // given
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE, false, false, false, false, emptyList());
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
  void testSome() {
    // given
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE, false, false, false, false, singletonList("key2"));
    Map<String, String> contextData = new HashMap<>();
    contextData.put("key1", "value1");
    contextData.put("key2", "value2");
    AttributesBuilder attributes = Attributes.builder();

    // when
    mapper.captureContextDataAttributes(attributes, contextData);

    // then
    assertThat(attributes.build()).containsOnly(attributeEntry("key2", "value2"));
  }

  @Test
  void testAll() {
    // given
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE, false, false, false, false, singletonList("*"));
    Map<String, String> contextData = new HashMap<>();
    contextData.put("key1", "value1");
    contextData.put("key2", "value2");
    AttributesBuilder attributes = Attributes.builder();

    // when
    mapper.captureContextDataAttributes(attributes, contextData);

    // then
    assertThat(attributes.build())
        .containsOnly(attributeEntry("key1", "value1"), attributeEntry("key2", "value2"));
  }

  @Test
  void testCaptureMapMessageDisabled() {
    // given
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE, false, false, false, false, singletonList("*"));

    StringMapMessage message = new StringMapMessage();
    message.put("key1", "value1");
    message.put("message", "value2");

    LogRecordBuilder logRecordBuilder = mock(LogRecordBuilder.class);
    AttributesBuilder attributes = Attributes.builder();

    // when
    mapper.captureMessage(logRecordBuilder, attributes, message);

    // then
    verify(logRecordBuilder).setBody("value2");
    assertThat(attributes.build()).isEmpty();
  }

  @Test
  void testCaptureMapMessageWithSpecialAttribute() {
    // given
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE, false, false, true, false, singletonList("*"));

    StringMapMessage message = new StringMapMessage();
    message.put("key1", "value1");
    message.put("message", "value2");

    LogRecordBuilder logRecordBuilder = mock(LogRecordBuilder.class);
    AttributesBuilder attributes = Attributes.builder();

    // when
    mapper.captureMessage(logRecordBuilder, attributes, message);

    // then
    verify(logRecordBuilder).setBody("value2");
    assertThat(attributes.build()).containsOnly(attributeEntry("log4j.map_message.key1", "value1"));
  }

  @Test
  void testCaptureMapMessageWithoutSpecialAttribute() {
    // given
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE, false, false, true, false, singletonList("*"));

    StringMapMessage message = new StringMapMessage();
    message.put("key1", "value1");
    message.put("key2", "value2");

    LogRecordBuilder logRecordBuilder = mock(LogRecordBuilder.class);
    AttributesBuilder attributes = Attributes.builder();

    // when
    mapper.captureMessage(logRecordBuilder, attributes, message);

    // then
    verify(logRecordBuilder, never()).setBody(anyString());
    assertThat(attributes.build())
        .containsOnly(
            attributeEntry("log4j.map_message.key1", "value1"),
            attributeEntry("log4j.map_message.key2", "value2"));
  }

  @Test
  void testCaptureStructuredDataMessage() {
    // given
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE, false, false, true, false, singletonList("*"));

    StructuredDataMessage message = new StructuredDataMessage("an id", "a message", "a type");
    message.put("key1", "value1");
    message.put("message", "value2");

    LogRecordBuilder logRecordBuilder = mock(LogRecordBuilder.class);
    AttributesBuilder attributes = Attributes.builder();

    // when
    mapper.captureMessage(logRecordBuilder, attributes, message);

    // then
    verify(logRecordBuilder).setBody("a message");
    assertThat(attributes.build())
        .containsOnly(
            attributeEntry("log4j.map_message.key1", "value1"),
            attributeEntry("log4j.map_message.message", "value2"));
  }

  private enum ContextDataAccessorImpl implements ContextDataAccessor<Map<String, String>> {
    INSTANCE;

    @Override
    @Nullable
    public String getValue(Map<String, String> contextData, String key) {
      return contextData.get(key);
    }

    @Override
    public void forEach(Map<String, String> contextData, BiConsumer<String, String> action) {
      contextData.forEach(action);
    }
  }
}
