/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources.internal;

import io.opentelemetry.sdk.autoconfigure.spi.internal.ComponentProvider;
import io.opentelemetry.sdk.autoconfigure.spi.internal.StructuredConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import java.util.function.Supplier;

/** Abstract class to simply {@link Resource} {@link ComponentProvider} implementations. */
abstract class ResourceComponentProvider implements ComponentProvider<Resource> {

  private final Supplier<Resource> supplier;

  ResourceComponentProvider(Supplier<Resource> supplier) {
    this.supplier = supplier;
  }

  @Override
  public Class<Resource> getType() {
    return Resource.class;
  }

  @Override
  public String getName() {
    // getName() is unused for Resource ComponentProviders
    return "unused";
  }

  @Override
  public Resource create(StructuredConfigProperties structuredConfigProperties) {
    return supplier.get();
  }
}
