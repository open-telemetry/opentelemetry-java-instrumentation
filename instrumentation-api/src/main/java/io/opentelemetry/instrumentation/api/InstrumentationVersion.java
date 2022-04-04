/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api;

/**
 * This class will be removed.
 *
 * @deprecated This class will be removed.
 */
@Deprecated
public final class InstrumentationVersion {
  public static final String VERSION =
      InstrumentationVersion.class.getPackage().getImplementationVersion();

  private InstrumentationVersion() {}
}
