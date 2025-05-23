/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources.internal;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ComponentProvider;
import io.opentelemetry.sdk.resources.Resource;
import java.util.function.Supplier;

/** Abstract class to simply {@link Resource} {@link ComponentProvider} implementations. */
abstract class ResourceComponentProvider implements ComponentProvider<Resource> {

  private final String name;
  private final Supplier<Resource> supplier;

  ResourceComponentProvider(String name, Supplier<Resource> supplier) {
    this.name = name;
    this.supplier = supplier;
  }

  @Override
  public Class<Resource> getType() {
    return Resource.class;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Resource create(DeclarativeConfigProperties declarativeConfigProperties) {
    return supplier.get();
  }
}
