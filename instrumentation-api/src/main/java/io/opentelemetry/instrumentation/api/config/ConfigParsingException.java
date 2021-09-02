/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.config;

public class ConfigParsingException extends RuntimeException {
  public ConfigParsingException(String message) {
    super(message);
  }

  public ConfigParsingException(String message, Throwable cause) {
    super(message, cause);
  }
}
