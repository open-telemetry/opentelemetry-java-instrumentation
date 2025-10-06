/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17.internal;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.opentelemetry.api.common.AttributeKey;
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
            ContextDataAccessorImpl.INSTANCE, false, false, false, false, emptyList(), false);
    Map<String, String> contextData = new HashMap<>();
    contextData.put("key1", "value1");
    contextData.put("key2", "value2");
    LogRecordBuilder builder = mock(LogRecordBuilder.class);

    // when
    mapper.captureContextDataAttributes(builder, contextData);

    // then
    verifyNoInteractions(builder);
  }

  @Test
  void testSome() {
    // given
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE,
            false,
            false,
            false,
            false,
            singletonList("key2"),
            false);
    Map<String, String> contextData = new HashMap<>();
    contextData.put("key1", "value1");
    contextData.put("key2", "value2");
    LogRecordBuilder builder = mock(LogRecordBuilder.class);

    // when
    mapper.captureContextDataAttributes(builder, contextData);

    // then
    verify(builder).setAttribute(AttributeKey.stringKey("key2"), "value2");
    verifyNoMoreInteractions(builder);
  }

  @Test
  void testAll() {
    // given
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE,
            false,
            false,
            false,
            false,
            singletonList("*"),
            false);
    Map<String, String> contextData = new HashMap<>();
    contextData.put("key1", "value1");
    contextData.put("key2", "value2");
    LogRecordBuilder builder = mock(LogRecordBuilder.class);

    // when
    mapper.captureContextDataAttributes(builder, contextData);

    // then
    verify(builder).setAttribute(AttributeKey.stringKey("key1"), "value1");
    verify(builder).setAttribute(AttributeKey.stringKey("key2"), "value2");
    verifyNoMoreInteractions(builder);
  }

  @Test
  void testCaptureMapMessageDisabled() {
    // given
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE,
            false,
            false,
            false,
            false,
            singletonList("*"),
            false);

    StringMapMessage message = new StringMapMessage();
    message.put("key1", "value1");
    message.put("message", "value2");

    LogRecordBuilder builder = mock(LogRecordBuilder.class);

    // when
    mapper.captureMessage(builder, message);

    // then
    verify(builder).setBody("value2");
    verifyNoMoreInteractions(builder);
  }

  @Test
  void testCaptureMapMessageWithSpecialAttribute() {
    // given
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE, false, false, true, false, singletonList("*"), false);

    StringMapMessage message = new StringMapMessage();
    message.put("key1", "value1");
    message.put("message", "value2");

    LogRecordBuilder builder = mock(LogRecordBuilder.class);

    // when
    mapper.captureMessage(builder, message);

    // then
    verify(builder).setBody("value2");
    verify(builder).setAttribute(AttributeKey.stringKey("log4j.map_message.key1"), "value1");
    verifyNoMoreInteractions(builder);
  }

  @Test
  void testCaptureMapMessageWithoutSpecialAttribute() {
    // given
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE, false, false, true, false, singletonList("*"), false);

    StringMapMessage message = new StringMapMessage();
    message.put("key1", "value1");
    message.put("key2", "value2");

    LogRecordBuilder builder = mock(LogRecordBuilder.class);

    // when
    mapper.captureMessage(builder, message);

    // then
    verify(builder, never()).setBody(anyString());
    verify(builder).setAttribute(AttributeKey.stringKey("log4j.map_message.key1"), "value1");
    verify(builder).setAttribute(AttributeKey.stringKey("log4j.map_message.key2"), "value2");
    verifyNoMoreInteractions(builder);
  }

  @Test
  void testCaptureStructuredDataMessage() {
    // given
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE, false, false, true, false, singletonList("*"), false);

    StructuredDataMessage message = new StructuredDataMessage("an id", "a message", "a type");
    message.put("key1", "value1");
    message.put("message", "value2");

    LogRecordBuilder builder = mock(LogRecordBuilder.class);

    // when
    mapper.captureMessage(builder, message);

    // then
    verify(builder).setBody("a message");
    verify(builder).setAttribute(AttributeKey.stringKey("log4j.map_message.key1"), "value1");
    verify(builder).setAttribute(AttributeKey.stringKey("log4j.map_message.message"), "value2");
    verifyNoMoreInteractions(builder);
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
