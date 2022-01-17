/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

final class MuzzleCompilationException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  MuzzleCompilationException(String message) {
    super(message);
  }
}
