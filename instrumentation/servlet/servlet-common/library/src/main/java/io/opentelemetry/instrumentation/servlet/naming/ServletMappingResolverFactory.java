/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.naming;

import io.opentelemetry.instrumentation.api.servlet.MappingResolver;
import java.util.Collection;
import java.util.function.Supplier;

public abstract class ServletMappingResolverFactory implements Supplier<MappingResolver> {

  protected abstract Collection<String> getMappings();

  @Override
  public final MappingResolver get() {
    Collection<String> mappings = getMappings();
    if (mappings == null) {
      return null;
    }

    return MappingResolver.build(mappings);
  }
}
