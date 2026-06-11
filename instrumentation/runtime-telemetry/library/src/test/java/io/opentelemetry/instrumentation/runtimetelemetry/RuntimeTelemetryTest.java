/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.Internal;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class RuntimeTelemetryTest {

  private BiConsumer<RuntimeTelemetryBuilder, Boolean> originalCallback;
  private Field callbackField;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() throws Exception {
    RuntimeTelemetryBuilder dummyBuilder = RuntimeTelemetry.builder(OpenTelemetry.noop());
    assertThat(dummyBuilder).isNotNull();

    callbackField = Internal.class.getDeclaredField("setCaptureGcCause");
    callbackField.setAccessible(true);
    originalCallback = (BiConsumer<RuntimeTelemetryBuilder, Boolean>) callbackField.get(null);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (callbackField != null) {
      callbackField.set(null, originalCallback);
    }
  }

  @Test
  @SuppressWarnings("ResultOfMethodCallIgnored")
  void testCaptureGcCauseDefault_V3PreviewEnabled() {
    OpenTelemetry openTelemetry = OpenTelemetry.noop();
    DeclarativeConfigProperties config = mock(DeclarativeConfigProperties.class);
    DeclarativeConfigProperties java17Config = mock(DeclarativeConfigProperties.class);

    when(config.getBoolean(anyString(), anyBoolean()))
        .thenAnswer(invocation -> invocation.getArgument(1));
    when(java17Config.getBoolean(anyString(), anyBoolean()))
        .thenAnswer(invocation -> invocation.getArgument(1));

    AtomicBoolean capturedValue = new AtomicBoolean(false);
    Internal.internalSetCaptureGcCause(
        (builder, capture) -> {
          capturedValue.set(capture);
          if (originalCallback != null) {
            originalCallback.accept(builder, capture);
          }
        });

    try (MockedStatic<DeclarativeConfigUtil> utilities = mockStatic(DeclarativeConfigUtil.class);
        MockedStatic<SemconvStability> stability = mockStatic(SemconvStability.class)) {

      utilities
          .when(
              () ->
                  DeclarativeConfigUtil.getInstrumentationConfig(
                      openTelemetry, "runtime_telemetry"))
          .thenReturn(config);
      utilities
          .when(
              () ->
                  DeclarativeConfigUtil.getInstrumentationConfig(
                      openTelemetry, "runtime_telemetry_java17"))
          .thenReturn(java17Config);

      stability.when(SemconvStability::v3Preview).thenReturn(true);

      Internal.configure(openTelemetry, true);
    }

    assertThat(capturedValue.get()).isTrue();
  }

  @Test
  @SuppressWarnings("ResultOfMethodCallIgnored")
  void testCaptureGcCauseDefault_V3PreviewDisabled() {
    OpenTelemetry openTelemetry = OpenTelemetry.noop();
    DeclarativeConfigProperties config = mock(DeclarativeConfigProperties.class);
    DeclarativeConfigProperties java17Config = mock(DeclarativeConfigProperties.class);

    when(config.getBoolean(anyString(), anyBoolean()))
        .thenAnswer(invocation -> invocation.getArgument(1));
    when(java17Config.getBoolean(anyString(), anyBoolean()))
        .thenAnswer(invocation -> invocation.getArgument(1));

    AtomicBoolean capturedValue = new AtomicBoolean(true);
    Internal.internalSetCaptureGcCause(
        (builder, capture) -> {
          capturedValue.set(capture);
          if (originalCallback != null) {
            originalCallback.accept(builder, capture);
          }
        });

    try (MockedStatic<DeclarativeConfigUtil> utilities = mockStatic(DeclarativeConfigUtil.class);
        MockedStatic<SemconvStability> stability = mockStatic(SemconvStability.class)) {

      utilities
          .when(
              () ->
                  DeclarativeConfigUtil.getInstrumentationConfig(
                      openTelemetry, "runtime_telemetry"))
          .thenReturn(config);
      utilities
          .when(
              () ->
                  DeclarativeConfigUtil.getInstrumentationConfig(
                      openTelemetry, "runtime_telemetry_java17"))
          .thenReturn(java17Config);

      stability.when(SemconvStability::v3Preview).thenReturn(false);

      Internal.configure(openTelemetry, true);
    }

    assertThat(capturedValue.get()).isFalse();
  }

  @Test
  void testCaptureGcCauseCanBeDisabled() {
    OpenTelemetry openTelemetry = OpenTelemetry.noop();
    DeclarativeConfigProperties config = mock(DeclarativeConfigProperties.class);
    DeclarativeConfigProperties java17Config = mock(DeclarativeConfigProperties.class);

    when(config.getBoolean("enabled", true)).thenReturn(true);
    when(config.getBoolean("capture_gc_cause", true)).thenReturn(false);
    when(config.getBoolean("capture_gc_cause", false)).thenReturn(false);
    when(java17Config.getBoolean("enabled", false)).thenReturn(false);
    when(java17Config.getBoolean("enable_all", false)).thenReturn(false);

    AtomicBoolean capturedValue = new AtomicBoolean(true);
    Internal.internalSetCaptureGcCause(
        (builder, capture) -> {
          capturedValue.set(capture);
          if (originalCallback != null) {
            originalCallback.accept(builder, capture);
          }
        });

    try (MockedStatic<DeclarativeConfigUtil> utilities = mockStatic(DeclarativeConfigUtil.class)) {
      utilities
          .when(
              () ->
                  DeclarativeConfigUtil.getInstrumentationConfig(
                      openTelemetry, "runtime_telemetry"))
          .thenReturn(config);
      utilities
          .when(
              () ->
                  DeclarativeConfigUtil.getInstrumentationConfig(
                      openTelemetry, "runtime_telemetry_java17"))
          .thenReturn(java17Config);

      Internal.configure(openTelemetry, true);
    }

    assertThat(capturedValue.get()).isFalse();
  }
}
