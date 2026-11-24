/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.common.internal;

import static io.opentelemetry.instrumentation.servlet.common.internal.BodyCaptureConfig.DEFAULT_CAPTURE_REQUEST_BODY_MAX_SIZE;
import static io.opentelemetry.instrumentation.servlet.common.internal.BodyCaptureConfig.createDefault;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BodyCaptureConfigTest {

  @Test
  void defaults() {
    BodyCaptureConfig config = createDefault();
    assertThat(config.isEnabled()).isFalse();
    assertThat(config.getMaxSize()).isEqualTo(DEFAULT_CAPTURE_REQUEST_BODY_MAX_SIZE);
    assertThat(config.getMediaTypes().getIncluded())
        .containsExactly("text/*", "*/json", "*+json", "*/xml", "+xml", "*/yaml", "*+yaml");
    assertThat(config.getMediaTypes().getExcluded()).containsExactly("multipart/*");
  }

  @Test
  void enable() {
    BodyCaptureConfig config = createDefault().setEnabled(true);
    assertThat(config.isEnabled()).isTrue();
    assertThat(config.getMaxSize()).isEqualTo(1024);
    assertThat(config.getMediaTypes().matches("application/json")).isTrue();
    assertThat(config.getMediaTypes().matches("text/plain")).isTrue();
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

  @Test
  void mediaTypesNormalized() {
    BodyCaptureConfig config = createDefault();
    config.setMediaTypes(
        IncludeExclude.builder().setIncluded("TEXT/*").setExcluded("MULTIPART/*").build());
    assertThat(config.getMediaTypes().matches("text/plain")).isTrue();
    assertThat(config.getMediaTypes().matches("application/json")).isFalse();
    assertThat(config.getMediaTypes().matches("multipart/form-data")).isFalse();
  }
}
