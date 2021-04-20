/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle.collector;

public class MuzzleCompilationException extends RuntimeException {
  public MuzzleCompilationException(String message) {
    super(message);
  }
}
