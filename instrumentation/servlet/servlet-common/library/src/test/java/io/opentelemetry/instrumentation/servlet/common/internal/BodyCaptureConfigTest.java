package io.opentelemetry.instrumentation.servlet.common.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class BodyCaptureConfigTest {

  @Test
  void defaults() {
    BodyCaptureConfig config = BodyCaptureConfig.createDefault();
    assertThat(config.isEnabled()).isFalse();
    assertThat(config.getMaxSize()).isEqualTo(1024);
  }

  @Test
  void enable() {
    BodyCaptureConfig config = BodyCaptureConfig.createDefault().setEnabled(true);
    assertThat(config.isEnabled()).isTrue();
    assertThat(config.getMaxSize()).isEqualTo(1024);
  }

  @Test
  void maxSize() {
    BodyCaptureConfig config = BodyCaptureConfig.createDefault().setMaxSize(2048);
    assertThat(config.getMaxSize()).isEqualTo(2048);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, -100})
  void maxSize_invalid(int value) {
    BodyCaptureConfig config = BodyCaptureConfig.createDefault().setMaxSize(value);
    assertThat(config.getMaxSize()).isEqualTo(1024);
  }
}
