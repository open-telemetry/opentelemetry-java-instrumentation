/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle.generation;

final class MuzzleCompilationException extends RuntimeException {
  MuzzleCompilationException(String message) {
    super(message);
  }
}
