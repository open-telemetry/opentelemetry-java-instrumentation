/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.universal;

/** Enum representing the type of class for instrumentation targeting. */
public enum ClassType {
  CONCRETE,
  ABSTRACT,
  INTERFACE
}
