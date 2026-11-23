/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.common.internal;

import static io.opentelemetry.instrumentation.servlet.common.internal.BodyCaptureConfig.DEFAULT_CAPTURE_REQUEST_BODY_MAX_SIZE;
import static io.opentelemetry.instrumentation.servlet.common.internal.BodyCaptureConfig.createDefault;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BodyCaptureConfigTest {

  @Test
  void defaults() {
    BodyCaptureConfig config = createDefault();
    assertThat(config.isEnabled()).isFalse();
    assertThat(config.getMaxSize()).isEqualTo(DEFAULT_CAPTURE_REQUEST_BODY_MAX_SIZE);
  }

  @Test
  void enable() {
    BodyCaptureConfig config = createDefault().setEnabled(true);
    assertThat(config.isEnabled()).isTrue();
    assertThat(config.getMaxSize()).isEqualTo(1024);
  }

  @Test
  void maxSize() {
    BodyCaptureConfig config = createDefault().setMaxSize(2048);
    assertThat(config.getMaxSize()).isEqualTo(2048);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, -100})
  void maxSize_invalid(int value) {
    BodyCaptureConfig config = createDefault().setMaxSize(value);
    assertThat(config.getMaxSize()).isEqualTo(DEFAULT_CAPTURE_REQUEST_BODY_MAX_SIZE);
  }
}
