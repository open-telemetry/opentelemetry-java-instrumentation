/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.common.internal;

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
    assertThat(config.getMaxSize()).isEqualTo(1024);
    assertThat(config.getMediaTypes().getIncluded())
        .containsExactlyInAnyOrder(
            "text/*", "*/json", "*+json", "*/xml", "*+xml", "*/yaml", "*+yaml");
    assertThat(config.getMediaTypes().getExcluded()).isEmpty();
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
    assertThat(config.getMaxSize()).isEqualTo(1024);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "text/plain",
        "application/json",
        "application/problem+json",
        "application/xml",
        "application/soap+xml",
        "application/yaml",
        "application/vnd.foo+yaml"
      })
  void defaultMediaTypesMatch(String mediaType) {
    assertThat(createDefault().getMediaTypes().matches(mediaType)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "application/octet-stream",
        "application/x-www-form-urlencoded",
        "multipart/form-data"
      })
  void defaultMediaTypesDoNotMatch(String mediaType) {
    assertThat(createDefault().getMediaTypes().matches(mediaType)).isFalse();
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

  @Test
  void mediaTypesTrySetEmpty() {
    BodyCaptureConfig config = createDefault();
    IncludeExclude defaultValue = config.getMediaTypes();
    assertThat(defaultValue.isEmpty()).isFalse();
    config.setMediaTypes(
        IncludeExclude.builder().setIncluded("text/*").setExcluded("multipart/*").build());
    assertThat(config.getMediaTypes()).isNotEqualTo(defaultValue);

    IncludeExclude empty = IncludeExclude.builder().build();
    assertThat(empty.isEmpty()).isTrue();
    config.setMediaTypes(empty);
    assertThat(config.getMediaTypes()).isEqualTo(defaultValue);
  }
}
