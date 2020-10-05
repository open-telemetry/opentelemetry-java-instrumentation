/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api;

public class InstrumentationVersion {
  public static final String VERSION =
      InstrumentationVersion.class.getPackage().getImplementationVersion();
}
