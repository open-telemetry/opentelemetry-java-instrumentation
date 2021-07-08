/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle.generation.collector;

public class MuzzleCompilationException extends RuntimeException {
  public MuzzleCompilationException(String message) {
    super(message);
  }
}
