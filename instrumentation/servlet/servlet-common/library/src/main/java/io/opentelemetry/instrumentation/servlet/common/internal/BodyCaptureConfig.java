/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.common.internal;

import static java.util.logging.Level.WARNING;
import static java.util.stream.Collectors.toList;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import java.util.List;
import java.util.Locale;
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
  private IncludeExclude mediaTypes;

  private BodyCaptureConfig() {
    this.enabled = false;
    this.maxSize = DEFAULT_CAPTURE_REQUEST_BODY_MAX_SIZE;
    this.mediaTypes =
        IncludeExclude.builder()
            .setIncluded("text/*", "*/json", "*+json", "*/xml", "+xml", "*/yaml", "*+yaml")
            .setExcluded("multipart/*")
            .build();
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

  /**
   * Sets the media types to capture.
   *
   * @param mediaTypes the media types to capture, values will be normalized to lower case for
   *     case-insensitive comparison
   * @return this
   */
  @CanIgnoreReturnValue
  public BodyCaptureConfig setMediaTypes(IncludeExclude mediaTypes) {
    this.mediaTypes =
        IncludeExclude.builder()
            .setIncluded(normalizeMediaTypePatterns(mediaTypes.getIncluded()))
            .setExcluded(normalizeMediaTypePatterns(mediaTypes.getExcluded()))
            .build();
    return this;
  }

  private static List<String> normalizeMediaTypePatterns(List<String> patterns) {
    return patterns.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(toList());
  }

  /**
   * Returns the media types to capture.
   *
   * @return the media types to capture
   */
  public IncludeExclude getMediaTypes() {
    return mediaTypes;
  }
}
