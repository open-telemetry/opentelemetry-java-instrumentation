/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsf.jakarta;

class RootCauseException extends RuntimeException {
  public RootCauseException(Throwable cause) {
    super(cause);
  }
}
