package io.opentelemetry.instrumentation.servlet.common.internal;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class BodyCaptureConfig {

  private static final Logger logger = Logger.getLogger(BodyCaptureConfig.class.getName());

  public static final int DEFAULT_CAPTURE_REQUEST_BODY_MAX_SIZE = 1024;

  private boolean enabled;
  private int maxSize;

  private BodyCaptureConfig() {
    this.enabled = false;
    this.maxSize = DEFAULT_CAPTURE_REQUEST_BODY_MAX_SIZE;
  }

  /**
   * Creates a default body capture configuration
   *
   * @return default body capture configuration
   */
  public static BodyCaptureConfig createDefault() {
    return new BodyCaptureConfig();
  }

  public boolean isEnabled() {
    return enabled;
  }

  @CanIgnoreReturnValue
  public BodyCaptureConfig setEnabled(boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  public int getMaxSize() {
    return maxSize;
  }

  @CanIgnoreReturnValue
  public BodyCaptureConfig setMaxSize(int maxSize) {
    if (maxSize <= 0) {
      logger.log(
          WARNING,
          "Invalid request body capture max size {0}, using default value {1}",
          new Object[] {maxSize, DEFAULT_CAPTURE_REQUEST_BODY_MAX_SIZE});
      maxSize = DEFAULT_CAPTURE_REQUEST_BODY_MAX_SIZE;
    }
    this.maxSize = maxSize;
    return this;
  }

  // TODO: add support for media types
}
