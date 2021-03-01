/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test;

import io.opentracing.contrib.dropwizard.Trace;

/**
 * Note: this has to stay outside of 'io.opentelemetry.javaagent' package to be considered for
 * instrumentation
 */
public class ClassToInstrument {
  @Trace
  public static void someMethod() {}
}
