/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17.internal;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.util.Arrays.asList;
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
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
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
            ContextDataAccessorImpl.INSTANCE, false, false, null, false, false, false, null, false);
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
            null,
            false,
            false,
            false,
            include("key2"),
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
            null,
            false,
            false,
            false,
            include("*"),
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
  void testCaptureEventNameFromContextDataWithCaptureAll() {
    // given
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE,
            false,
            false,
            null,
            false,
            false,
            false,
            include("*"),
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
  void testWildcardPatterns() {
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE,
            false,
            false,
            null,
            false,
            false,
            false,
            include("request-?d", "user.*"),
            false);
    Map<String, String> contextData = new HashMap<>();
    contextData.put("request-id", "123");
    contextData.put("request-name", "ignored");
    contextData.put("user.name", "alice");
    LogRecordBuilder builder = mock(LogRecordBuilder.class);

    mapper.captureContextDataAttributes(builder, contextData);

    verify(builder).setAttribute(stringKey("request-id"), "123");
    verify(builder).setAttribute(stringKey("user.name"), "alice");
    verifyNoMoreInteractions(builder);
  }

  @Test
  void testExcludeOnly() {
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE,
            false,
            false,
            null,
            false,
            false,
            false,
            IncludeExclude.builder().setExcluded(singletonList("*secret*")).build()::matches,
            false);
    Map<String, String> contextData = new HashMap<>();
    contextData.put("request-id", "123");
    contextData.put("client-secret", "ignored");
    LogRecordBuilder builder = mock(LogRecordBuilder.class);

    mapper.captureContextDataAttributes(builder, contextData);

    verify(builder).setAttribute(stringKey("request-id"), "123");
    verifyNoMoreInteractions(builder);
  }

  @Test
  void testExclusionsTakePrecedence() {
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE,
            false,
            false,
            null,
            false,
            false,
            false,
            IncludeExclude.builder()
                    .setIncluded(singletonList("request-*"))
                    .setExcluded(singletonList("*-secret"))
                    .build()
                ::matches,
            false);
    Map<String, String> contextData = new HashMap<>();
    contextData.put("request-id", "123");
    contextData.put("request-secret", "ignored");
    LogRecordBuilder builder = mock(LogRecordBuilder.class);

    mapper.captureContextDataAttributes(builder, contextData);

    verify(builder).setAttribute(stringKey("request-id"), "123");
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
            null,
            false,
            false,
            false,
            include("*"),
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
            include("*"),
            false,
            false,
            false,
            include("*"),
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
            include("*"),
            false,
            false,
            false,
            include("*"),
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
            include("*"),
            false,
            false,
            false,
            include("*"),
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
  void testCaptureMapMessageWithSelector() {
    // given
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE,
            false,
            false,
            include("order-*", "user-?"),
            false,
            false,
            false,
            null,
            false);

    StringMapMessage message = new StringMapMessage();
    message.put("order-id", "123");
    message.put("user-1", "alice");
    message.put("user-22", "ignored");
    message.put("other", "ignored");

    LogRecordBuilder builder = mock(LogRecordBuilder.class);

    // when
    mapper.captureMessage(builder, message);

    // then
    verify(builder).setAttribute(stringKey("log4j.map_message.order-id"), "123");
    verify(builder).setAttribute(stringKey("log4j.map_message.user-1"), "alice");
    verifyNoMoreInteractions(builder);
  }

  @Test
  void testCaptureMapMessageExclusionsTakePrecedence() {
    // given
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE,
            false,
            false,
            IncludeExclude.builder()
                    .setIncluded(singletonList("order-*"))
                    .setExcluded(singletonList("*-secret"))
                    .build()
                ::matches,
            false,
            false,
            false,
            null,
            false);

    StringMapMessage message = new StringMapMessage();
    message.put("order-id", "123");
    message.put("order-secret", "ignored");

    LogRecordBuilder builder = mock(LogRecordBuilder.class);

    // when
    mapper.captureMessage(builder, message);

    // then
    verify(builder).setAttribute(stringKey("log4j.map_message.order-id"), "123");
    verifyNoMoreInteractions(builder);
  }

  @Test
  void testCaptureTemplateAndArguments() {
    // given
    LogEventMapper<Map<String, String>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE, false, false, null, false, true, true, null, false);
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
            ContextDataAccessorImpl.INSTANCE, false, false, null, false, false, false, null, false);
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

  private static Predicate<String> include(String... patterns) {
    return IncludeExclude.builder().setIncluded(asList(patterns)).build()::matches;
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
