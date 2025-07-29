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
import java.util.Collections;
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

  @Test
  void testCaptureAttributeArray() {
    AttributesBuilder builder = Attributes.builder();

    LoggingEventMapper.captureAttribute(builder, "booleanArray", new boolean[] {true});
    LoggingEventMapper.captureAttribute(builder, "BooleanArray", new Boolean[] {true});

    LoggingEventMapper.captureAttribute(builder, "byteArray", new byte[] {2});
    LoggingEventMapper.captureAttribute(builder, "ByteArray", new Byte[] {2});

    LoggingEventMapper.captureAttribute(builder, "shortArray", new short[] {2});
    LoggingEventMapper.captureAttribute(builder, "ShortArray", new Short[] {2});

    LoggingEventMapper.captureAttribute(builder, "intArray", new int[] {2});
    LoggingEventMapper.captureAttribute(builder, "IntegerArray", new Integer[] {2});

    LoggingEventMapper.captureAttribute(builder, "longArray", new long[] {2});
    LoggingEventMapper.captureAttribute(builder, "LongArray", new Long[] {2L});

    LoggingEventMapper.captureAttribute(builder, "floatArray", new float[] {2.0f});
    LoggingEventMapper.captureAttribute(builder, "FloatArray", new Float[] {2.0f});

    LoggingEventMapper.captureAttribute(builder, "doubleArray", new double[] {2.0});
    LoggingEventMapper.captureAttribute(builder, "DoubleArray", new Double[] {2.0});

    LoggingEventMapper.captureAttribute(builder, "ObjectArray", new Object[] {"test"});
    LoggingEventMapper.captureAttribute(builder, "List", Collections.singletonList("test"));
    LoggingEventMapper.captureAttribute(builder, "Set", Collections.singleton("test"));

    assertThat(builder.build())
        .containsOnly(
            entry(AttributeKey.booleanArrayKey("booleanArray"), singletonList(true)),
            entry(AttributeKey.booleanArrayKey("BooleanArray"), singletonList(true)),
            entry(AttributeKey.longArrayKey("byteArray"), singletonList(2L)),
            entry(AttributeKey.longArrayKey("ByteArray"), singletonList(2L)),
            entry(AttributeKey.longArrayKey("shortArray"), singletonList(2L)),
            entry(AttributeKey.longArrayKey("ShortArray"), singletonList(2L)),
            entry(AttributeKey.longArrayKey("intArray"), singletonList(2L)),
            entry(AttributeKey.longArrayKey("IntegerArray"), singletonList(2L)),
            entry(AttributeKey.longArrayKey("longArray"), singletonList(2L)),
            entry(AttributeKey.longArrayKey("LongArray"), singletonList(2L)),
            entry(AttributeKey.doubleArrayKey("floatArray"), singletonList(2.0)),
            entry(AttributeKey.doubleArrayKey("FloatArray"), singletonList(2.0)),
            entry(AttributeKey.doubleArrayKey("doubleArray"), singletonList(2.0)),
            entry(AttributeKey.doubleArrayKey("DoubleArray"), singletonList(2.0)),
            entry(AttributeKey.stringArrayKey("ObjectArray"), singletonList("test")),
            entry(AttributeKey.stringArrayKey("List"), singletonList("test")),
            entry(AttributeKey.stringArrayKey("Set"), singletonList("test")));
  }
}
