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

import io.opentelemetry.api.incubator.common.ExtendedAttributeKey;
import io.opentelemetry.api.incubator.common.ExtendedAttributes;
import io.opentelemetry.api.incubator.common.ExtendedAttributesBuilder;
import io.opentelemetry.api.logs.LogRecordBuilder;
import java.util.Arrays;
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
    LogEventMapper<Map<String, Object>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE, false, false, false, false, emptyList());
    Map<String, Object> contextData = new HashMap<>();
    contextData.put("key1", "value1");
    contextData.put("key2", "value2");
    ExtendedAttributesBuilder attributes = ExtendedAttributes.builder();

    // when
    mapper.captureContextDataAttributes(attributes, contextData);

    // then
    assertThat(attributes.build().asAttributes()).isEmpty();
  }

  @Test
  void testSome() {
    // given
    LogEventMapper<Map<String, Object>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE, false, false, false, false, singletonList("key2"));
    Map<String, Object> contextData = new HashMap<>();
    contextData.put("key1", "value1");
    contextData.put("key2", "value2");
    ExtendedAttributesBuilder attributes = ExtendedAttributes.builder();

    // when
    mapper.captureContextDataAttributes(attributes, contextData);

    // then
    assertThat(attributes.build().asAttributes()).containsOnly(attributeEntry("key2", "value2"));
  }

  @Test
  void testAll() {
    // given
    LogEventMapper<Map<String, Object>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE, false, false, false, false, singletonList("*"));
    Map<String, Object> contextData = new HashMap<>();
    contextData.put("key1", "value1");
    contextData.put("key2", "value2");
    ExtendedAttributesBuilder attributes = ExtendedAttributes.builder();

    // when
    mapper.captureContextDataAttributes(attributes, contextData);

    // then
    assertThat(attributes.build().asAttributes())
        .containsOnly(attributeEntry("key1", "value1"), attributeEntry("key2", "value2"));
  }

  @Test
  void testCaptureMapMessageDisabled() {
    // given
    LogEventMapper<Map<String, Object>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE, false, false, false, false, singletonList("*"));

    StringMapMessage message = new StringMapMessage();
    message.put("key1", "value1");
    message.put("message", "value2");

    LogRecordBuilder logRecordBuilder = mock(LogRecordBuilder.class);
    ExtendedAttributesBuilder attributes = ExtendedAttributes.builder();

    // when
    mapper.captureMessage(logRecordBuilder, attributes, message);

    // then
    verify(logRecordBuilder).setBody("value2");
    assertThat(attributes.build().asAttributes()).isEmpty();
  }

  @Test
  void testCaptureMapMessageWithSpecialAttribute() {
    // given
    LogEventMapper<Map<String, Object>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE, false, false, true, false, singletonList("*"));

    StringMapMessage message = new StringMapMessage();
    message.put("key1", "value1");
    message.put("message", "value2");

    LogRecordBuilder logRecordBuilder = mock(LogRecordBuilder.class);
    ExtendedAttributesBuilder attributes = ExtendedAttributes.builder();

    // when
    mapper.captureMessage(logRecordBuilder, attributes, message);

    // then
    verify(logRecordBuilder).setBody("value2");
    assertThat(attributes.build().asAttributes())
        .containsOnly(attributeEntry("log4j.map_message.key1", "value1"));
  }

  @Test
  void testCaptureMapMessageWithoutSpecialAttribute() {
    // given
    LogEventMapper<Map<String, Object>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE, false, false, true, false, singletonList("*"));

    StringMapMessage message = new StringMapMessage();
    message.put("key1", "value1");
    message.put("key2", "value2");

    LogRecordBuilder logRecordBuilder = mock(LogRecordBuilder.class);
    ExtendedAttributesBuilder attributes = ExtendedAttributes.builder();

    // when
    mapper.captureMessage(logRecordBuilder, attributes, message);

    // then
    verify(logRecordBuilder, never()).setBody(anyString());
    assertThat(attributes.build().asAttributes())
        .containsOnly(
            attributeEntry("log4j.map_message.key1", "value1"),
            attributeEntry("log4j.map_message.key2", "value2"));
  }

  @Test
  void testCaptureStructuredDataMessage() {
    // given
    LogEventMapper<Map<String, Object>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE, false, false, true, false, singletonList("*"));

    StructuredDataMessage message = new StructuredDataMessage("an id", "a message", "a type");
    message.put("key1", "value1");
    message.put("message", "value2");

    LogRecordBuilder logRecordBuilder = mock(LogRecordBuilder.class);
    ExtendedAttributesBuilder attributes = ExtendedAttributes.builder();

    // when
    mapper.captureMessage(logRecordBuilder, attributes, message);

    // then
    verify(logRecordBuilder).setBody("a message");
    assertThat(attributes.build().asAttributes())
        .containsOnly(
            attributeEntry("log4j.map_message.key1", "value1"),
            attributeEntry("log4j.map_message.message", "value2"));
  }

  @Test
  void testObjectsInContextData() {
    // given
    LogEventMapper<Map<String, Object>> mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE, false, false, true, false, singletonList("*"));

    Map<String, Object> contextData = new HashMap<>();

    // scalars
    contextData.put("string", "value");
    contextData.put("int", 10);
    contextData.put("long", 11L);
    contextData.put("float", 12f);
    contextData.put("double", 13d);
    contextData.put("boolean", false);

    // arrays
    contextData.put("stringArray", new String[] {"one", "two", "three"});
    contextData.put("intArray", new int[] {1, 2, 3});
    contextData.put("longArray", new long[] {4L, 5L, 6L});
    contextData.put("floatArray", new float[] {7f, 8f, 9f});
    contextData.put("doubleArray", new double[] {10d, 11d, 12d});
    contextData.put("booleanArray", new boolean[] {true, false, false});

    // lists
    contextData.put("stringList", Arrays.asList("one", "two", "three"));
    contextData.put("intList", Arrays.asList(1, 2, 3));
    contextData.put("longList", Arrays.asList(4L, 5L, 6L));
    contextData.put("floatList", Arrays.asList(7f, 8f, 9f));
    contextData.put("doubleList", Arrays.asList(10d, 11d, 12d));
    contextData.put("booleanList", Arrays.asList(true, false, false));

    Map<String, Object> map = new HashMap<>();
    map.put("entry1", "value1");
    map.put("entry2", 28);
    map.put("entry3", new int[] {1, 2, 3});
    contextData.put("map", map);

    ExtendedAttributesBuilder attributes = ExtendedAttributes.builder();

    // when
    mapper.captureContextDataAttributes(attributes, contextData);

    // then
    ExtendedAttributes result = attributes.build();

    // scalars
    assertThat(result.get(ExtendedAttributeKey.stringKey("string"))).isEqualTo("value");
    assertThat(result.get(ExtendedAttributeKey.longKey("int"))).isEqualTo(10L);
    assertThat(result.get(ExtendedAttributeKey.longKey("long"))).isEqualTo(11L);
    assertThat(result.get(ExtendedAttributeKey.doubleKey("float"))).isEqualTo(12d);
    assertThat(result.get(ExtendedAttributeKey.doubleKey("double"))).isEqualTo(13f);
    assertThat(result.get(ExtendedAttributeKey.booleanKey("boolean"))).isEqualTo(false);

    assertThat(result.get(ExtendedAttributeKey.stringArrayKey("stringArray")))
        .isEqualTo(Arrays.asList("one", "two", "three"));
    assertThat(result.get(ExtendedAttributeKey.longArrayKey("intArray")))
        .isEqualTo(Arrays.asList(1L, 2L, 3L));
    assertThat(result.get(ExtendedAttributeKey.longArrayKey("longArray")))
        .isEqualTo(Arrays.asList(4L, 5L, 6L));
    assertThat(result.get(ExtendedAttributeKey.doubleArrayKey("floatArray")))
        .isEqualTo(Arrays.asList(7d, 8d, 9d));
    assertThat(result.get(ExtendedAttributeKey.doubleArrayKey("doubleArray")))
        .isEqualTo(Arrays.asList(10d, 11d, 12d));
    assertThat(result.get(ExtendedAttributeKey.booleanArrayKey("booleanArray")))
        .isEqualTo(Arrays.asList(true, false, false));

    assertThat(result.get(ExtendedAttributeKey.stringArrayKey("stringList")))
        .isEqualTo(Arrays.asList("one", "two", "three"));
    assertThat(result.get(ExtendedAttributeKey.longArrayKey("intList")))
        .isEqualTo(Arrays.asList(1L, 2L, 3L));
    assertThat(result.get(ExtendedAttributeKey.longArrayKey("longList")))
        .isEqualTo(Arrays.asList(4L, 5L, 6L));
    assertThat(result.get(ExtendedAttributeKey.doubleArrayKey("floatList")))
        .isEqualTo(Arrays.asList(7d, 8d, 9d));
    assertThat(result.get(ExtendedAttributeKey.doubleArrayKey("doubleList")))
        .isEqualTo(Arrays.asList(10d, 11d, 12d));
    assertThat(result.get(ExtendedAttributeKey.booleanArrayKey("booleanList")))
        .isEqualTo(Arrays.asList(true, false, false));

    Map<ExtendedAttributeKey<?>, Object> expected = new HashMap<>();
    expected.put(ExtendedAttributeKey.stringKey("entry1"), "value1");
    expected.put(ExtendedAttributeKey.longKey("entry2"), 28L);
    expected.put(ExtendedAttributeKey.longArrayKey("entry3"), Arrays.asList(1L, 2L, 3L));

    ExtendedAttributes actual = result.get(ExtendedAttributeKey.extendedAttributesKey("map"));
    assertThat(actual).isNotNull();
    assertThat(actual.asMap()).containsExactlyInAnyOrderEntriesOf(expected);
  }

  private enum ContextDataAccessorImpl implements ContextDataAccessor<Map<String, Object>, Object> {
    INSTANCE;

    @Override
    @Nullable
    public Object getValue(Map<String, Object> contextData, String key) {
      return contextData.get(key);
    }

    @Override
    public void forEach(Map<String, Object> contextData, BiConsumer<String, Object> action) {
      contextData.forEach(action);
    }
  }
}
