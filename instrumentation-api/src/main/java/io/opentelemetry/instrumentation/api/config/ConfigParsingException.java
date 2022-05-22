/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.config;

class ConfigParsingException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  ConfigParsingException(String message) {
    super(message);
  }
}
