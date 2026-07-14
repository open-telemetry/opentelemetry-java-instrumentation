/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17.internal;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.context.Context;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.logging.log4j.message.StringMapMessage;
import org.apache.logging.log4j.message.StructuredDataMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LogEventMapperTest {

  @Test
  void testDefault() {
    // given
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE,
            false,
            false,
            false,
            false,
            false,
            false,
            emptyList(),
            emptyList(),
            false);
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
            false,
            false,
            singletonList("key2"),
            emptyList(),
            false);
    Map<String, String> contextData = new HashMap<>();
    contextData.put("key1", "value1");
    contextData.put("key2", "value2");
    LogRecordBuilder builder = mock(LogRecordBuilder.class);

    // when
    mapper.captureContextDataAttributes(builder, contextData);

    // then
    verify(builder).setAttribute(stringKey("key2"), "value2");
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
            false,
            false,
            singletonList("*"),
            emptyList(),
            false);
    Map<String, String> contextData = new HashMap<>();
    contextData.put("key1", "value1");
    contextData.put("key2", "value2");
    LogRecordBuilder builder = mock(LogRecordBuilder.class);

    // when
    mapper.captureContextDataAttributes(builder, contextData);

    // then
    verify(builder).setAttribute(stringKey("key1"), "value1");
    verify(builder).setAttribute(stringKey("key2"), "value2");
    verifyNoMoreInteractions(builder);
  }

  @Test
  void testAllWithExclude() {
    // given - "*" captures everything except the configured excludes
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE,
            false,
            false,
            false,
            false,
            false,
            false,
            singletonList("*"),
            singletonList("key2"),
            false);
    Map<String, String> contextData = new HashMap<>();
    contextData.put("key1", "value1");
    contextData.put("key2", "value2");
    LogRecordBuilder builder = mock(LogRecordBuilder.class);

    // when
    mapper.captureContextDataAttributes(builder, contextData);

    // then
    verify(builder).setAttribute(stringKey("key1"), "value1");
    verify(builder, never()).setAttribute(stringKey("key2"), "value2");
    verifyNoMoreInteractions(builder);
  }

  @Test
  void testGlobIncludeAndExclude() {
    // given - includes/excludes support glob wildcards
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE,
            false,
            false,
            false,
            false,
            false,
            false,
            singletonList("user.*"),
            singletonList("user.secret*"),
            false);
    Map<String, String> contextData = new HashMap<>();
    contextData.put("user.id", "value1");
    contextData.put("user.secretToken", "value2");
    contextData.put("other", "value3");
    LogRecordBuilder builder = mock(LogRecordBuilder.class);

    // when
    mapper.captureContextDataAttributes(builder, contextData);

    // then - only user.id matches the include glob and not the exclude glob
    verify(builder).setAttribute(stringKey("user.id"), "value1");
    verifyNoMoreInteractions(builder);
  }

  @Test
  void testExcludeRequiresInclude() {
    // given - excludes have no effect without a non-empty include list
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE,
            false,
            false,
            false,
            false,
            false,
            false,
            emptyList(),
            singletonList("key2"),
            false);
    Map<String, String> contextData = new HashMap<>();
    contextData.put("key1", "value1");
    contextData.put("key2", "value2");
    LogRecordBuilder builder = mock(LogRecordBuilder.class);

    // when
    mapper.captureContextDataAttributes(builder, contextData);

    // then - nothing is captured
    verifyNoInteractions(builder);
  }

  @Test
  void testCaptureEventNameFromContextDataWithCaptureAll() {
    // given
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE,
            false,
            false,
            false,
            false,
            false,
            false,
            singletonList("*"),
            emptyList(),
            false);
    Map<String, String> contextData = new HashMap<>();
    contextData.put("key1", "value1");
    contextData.put("otel.event.name", "MyEventName");
    LogRecordBuilder builder = mock(LogRecordBuilder.class);

    // when
    mapper.captureContextDataAttributes(builder, contextData);

    // then
    verify(builder).setAttribute(stringKey("key1"), "value1");
    verify(builder).setEventName("MyEventName");
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
            false,
            false,
            singletonList("*"),
            emptyList(),
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

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testCaptureMapMessageWithSpecialAttribute(boolean v3Preview) {
    // given
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE,
            false,
            false,
            true,
            false,
            false,
            false,
            singletonList("*"),
            emptyList(),
            v3Preview);

    StringMapMessage message = new StringMapMessage();
    message.put("key1", "value1");
    message.put("message", "value2");

    LogRecordBuilder builder = mock(LogRecordBuilder.class);

    // when
    mapper.captureMessage(builder, message);

    // then
    verify(builder).setBody("value2");
    verify(builder)
        .setAttribute(stringKey(v3Preview ? "key1" : "log4j.map_message.key1"), "value1");
    verifyNoMoreInteractions(builder);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testCaptureMapMessageWithoutSpecialAttribute(boolean v3Preview) {
    // given
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE,
            false,
            false,
            true,
            false,
            false,
            false,
            singletonList("*"),
            emptyList(),
            v3Preview);

    StringMapMessage message = new StringMapMessage();
    message.put("key1", "value1");
    message.put("key2", "value2");

    LogRecordBuilder builder = mock(LogRecordBuilder.class);

    // when
    mapper.captureMessage(builder, message);

    // then
    verify(builder, never()).setBody(anyString());
    verify(builder)
        .setAttribute(stringKey(v3Preview ? "key1" : "log4j.map_message.key1"), "value1");
    verify(builder)
        .setAttribute(stringKey(v3Preview ? "key2" : "log4j.map_message.key2"), "value2");
    verifyNoMoreInteractions(builder);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testCaptureStructuredDataMessage(boolean v3Preview) {
    // given
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE,
            false,
            false,
            true,
            false,
            false,
            false,
            singletonList("*"),
            emptyList(),
            v3Preview);

    StructuredDataMessage message = new StructuredDataMessage("an id", "a message", "a type");
    message.put("key1", "value1");
    message.put("message", "value2");

    LogRecordBuilder builder = mock(LogRecordBuilder.class);

    // when
    mapper.captureMessage(builder, message);

    // then
    verify(builder).setBody("a message");
    verify(builder)
        .setAttribute(stringKey(v3Preview ? "key1" : "log4j.map_message.key1"), "value1");
    verify(builder)
        .setAttribute(stringKey(v3Preview ? "message" : "log4j.map_message.message"), "value2");
    verifyNoMoreInteractions(builder);
  }

  @Test
  void testCaptureTemplateAndArguments() {
    // given
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE,
            false,
            false,
            false,
            false,
            true,
            true,
            emptyList(),
            emptyList(),
            false);
    ParameterizedMessage message = new ParameterizedMessage("hello {}", "world");
    LogRecordBuilder builder = mock(LogRecordBuilder.class);

    // when
    mapper.mapLogEvent(
        builder,
        message,
        Level.INFO,
        null,
        null,
        new HashMap<>(),
        "main",
        1,
        () -> null,
        Context.root());

    // then
    verify(builder).setBody("hello world");
    verify(builder).setAttribute(stringKey("log.body.template"), "hello {}");
    verify(builder).setAttribute(stringArrayKey("log.body.parameters"), singletonList("world"));
  }

  @Test
  void testCaptureTemplateAndArgumentsDisabledByDefault() {
    // given
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE,
            false,
            false,
            false,
            false,
            false,
            false,
            emptyList(),
            emptyList(),
            false);
    ParameterizedMessage message = new ParameterizedMessage("hello {}", "world");
    LogRecordBuilder builder = mock(LogRecordBuilder.class);

    // when
    mapper.mapLogEvent(
        builder,
        message,
        Level.INFO,
        null,
        null,
        new HashMap<>(),
        "main",
        1,
        () -> null,
        Context.root());

    // then
    verify(builder).setBody("hello world");
    verify(builder, never()).setAttribute(eq(stringKey("log.body.template")), anyString());
    verify(builder, never()).setAttribute(eq(stringArrayKey("log.body.parameters")), any());
  }

  private enum ContextDataAccessorImpl implements ContextDataAccessor<Map<String, String>> {
    INSTANCE;

    @Override
    public String getValue(Map<String, String> contextData, String key) {
      return contextData.get(key);
    }

    @Override
    public void forEach(Map<String, String> contextData, BiConsumer<String, String> action) {
      contextData.forEach(action);
    }
  }
}
