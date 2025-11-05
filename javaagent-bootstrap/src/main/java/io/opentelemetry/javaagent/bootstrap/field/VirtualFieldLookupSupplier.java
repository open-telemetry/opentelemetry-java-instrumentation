/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.field;

import java.lang.invoke.MethodHandles;
import java.util.function.Supplier;

/**
 * Helper class that provides a MethodHandles.Lookup that allows defining classes in this package.
 */
public final class VirtualFieldLookupSupplier implements Supplier<MethodHandles.Lookup> {

  @Override
  public MethodHandles.Lookup get() {
    return MethodHandles.lookup();
  }
}
