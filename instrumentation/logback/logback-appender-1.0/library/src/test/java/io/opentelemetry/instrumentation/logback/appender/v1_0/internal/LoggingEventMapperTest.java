/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0.internal;

import static io.opentelemetry.api.common.AttributeKey.booleanArrayKey;
import static io.opentelemetry.api.common.AttributeKey.doubleArrayKey;
import static io.opentelemetry.api.common.AttributeKey.longArrayKey;
import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.opentelemetry.api.logs.LogRecordBuilder;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;

@DisabledInNativeImage // conflict with Mockito
class LoggingEventMapperTest {

  @Test
  void testDefault() {
    // given
    LoggingEventMapper mapper = LoggingEventMapper.builder().build();
    Map<String, String> contextData = new HashMap<>();
    contextData.put("key1", "value1");
    contextData.put("key2", "value2");
    LogRecordBuilder builder = mock(LogRecordBuilder.class);

    // when
    mapper.captureMdcAttributes(builder, contextData);

    // then
    verifyNoInteractions(builder);
  }

  @Test
  void testSome() {
    // given
    LoggingEventMapper mapper =
        LoggingEventMapper.builder().setCaptureMdcAttributes(singletonList("key2")).build();
    Map<String, String> contextData = new HashMap<>();
    contextData.put("key1", "value1");
    contextData.put("key2", "value2");
    LogRecordBuilder builder = mock(LogRecordBuilder.class);

    // when
    mapper.captureMdcAttributes(builder, contextData);

    // then
    verify(builder).setAttribute(stringKey("key2"), "value2");
    verifyNoMoreInteractions(builder);
  }

  @Test
  void testAll() {
    // given
    LoggingEventMapper mapper =
        LoggingEventMapper.builder().setCaptureMdcAttributes(singletonList("*")).build();
    Map<String, String> contextData = new HashMap<>();
    contextData.put("key1", "value1");
    contextData.put("key2", "value2");
    LogRecordBuilder builder = mock(LogRecordBuilder.class);

    // when
    mapper.captureMdcAttributes(builder, contextData);

    // then
    verify(builder).setAttribute(stringKey("key1"), "value1");
    verify(builder).setAttribute(stringKey("key2"), "value2");
    verifyNoMoreInteractions(builder);
  }

  @Test
  void testCaptureAttributeArray() {
    LogRecordBuilder builder = mock(LogRecordBuilder.class);

    LoggingEventMapper.captureAttribute(builder, false, "booleanArray", new boolean[] {true});
    LoggingEventMapper.captureAttribute(builder, false, "BooleanArray", new Boolean[] {true});

    LoggingEventMapper.captureAttribute(builder, false, "byteArray", new byte[] {2});
    LoggingEventMapper.captureAttribute(builder, false, "ByteArray", new Byte[] {2});

    LoggingEventMapper.captureAttribute(builder, false, "shortArray", new short[] {2});
    LoggingEventMapper.captureAttribute(builder, false, "ShortArray", new Short[] {2});

    LoggingEventMapper.captureAttribute(builder, false, "intArray", new int[] {2});
    LoggingEventMapper.captureAttribute(builder, false, "IntegerArray", new Integer[] {2});

    LoggingEventMapper.captureAttribute(builder, false, "longArray", new long[] {2});
    LoggingEventMapper.captureAttribute(builder, false, "LongArray", new Long[] {2L});

    LoggingEventMapper.captureAttribute(builder, false, "floatArray", new float[] {2.0f});
    LoggingEventMapper.captureAttribute(builder, false, "FloatArray", new Float[] {2.0f});

    LoggingEventMapper.captureAttribute(builder, false, "doubleArray", new double[] {2.0});
    LoggingEventMapper.captureAttribute(builder, false, "DoubleArray", new Double[] {2.0});

    LoggingEventMapper.captureAttribute(builder, false, "ObjectArray", new Object[] {"test"});
    LoggingEventMapper.captureAttribute(builder, false, "List", singletonList("test"));
    LoggingEventMapper.captureAttribute(builder, false, "Set", singleton("test"));

    verify(builder).setAttribute(booleanArrayKey("booleanArray"), singletonList(true));
    verify(builder).setAttribute(booleanArrayKey("BooleanArray"), singletonList(true));
    verify(builder).setAttribute(longArrayKey("byteArray"), singletonList(2L));
    verify(builder).setAttribute(longArrayKey("ByteArray"), singletonList(2L));
    verify(builder).setAttribute(longArrayKey("shortArray"), singletonList(2L));
    verify(builder).setAttribute(longArrayKey("ShortArray"), singletonList(2L));
    verify(builder).setAttribute(longArrayKey("intArray"), singletonList(2L));
    verify(builder).setAttribute(longArrayKey("IntegerArray"), singletonList(2L));
    verify(builder).setAttribute(longArrayKey("longArray"), singletonList(2L));
    verify(builder).setAttribute(longArrayKey("LongArray"), singletonList(2L));
    verify(builder).setAttribute(doubleArrayKey("floatArray"), singletonList(2.0));
    verify(builder).setAttribute(doubleArrayKey("FloatArray"), singletonList(2.0));
    verify(builder).setAttribute(doubleArrayKey("doubleArray"), singletonList(2.0));
    verify(builder).setAttribute(doubleArrayKey("DoubleArray"), singletonList(2.0));
    verify(builder).setAttribute(stringArrayKey("ObjectArray"), singletonList("test"));
    verify(builder).setAttribute(stringArrayKey("List"), singletonList("test"));
    verify(builder).setAttribute(stringArrayKey("Set"), singletonList("test"));
    verifyNoMoreInteractions(builder);
  }
}
