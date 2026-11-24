/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.common.internal;

import static java.util.logging.Level.WARNING;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.logging.Logger;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class BodyCaptureConfig {

  private static final Logger logger = Logger.getLogger(BodyCaptureConfig.class.getName());

  /** Default maximum size of the request body to capture. */
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

  /**
   * Returns true if the request body capture is enabled, false otherwise
   *
   * @return true when enabled, false otherwise
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Toggles the request body feature on or off.
   *
   * @param enabled true to enable, false to disable
   * @return this
   */
  @CanIgnoreReturnValue
  public BodyCaptureConfig setEnabled(boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  /**
   * Returns the maximum size of the request body to capture.
   *
   * @return the maximum size of the request body to capture, returned value is >= 1
   */
  public int getMaxSize() {
    return maxSize;
  }

  /**
   * Sets the maximum size of the request body to capture
   *
   * @param maxSize the maximum size of the request body to capture, if <=0, the default value of
   *     {@link #DEFAULT_CAPTURE_REQUEST_BODY_MAX_SIZE} will be used
   * @return this
   */
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
}
