/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.naming;

import io.opentelemetry.instrumentation.api.servlet.MappingResolver;
import java.util.Collection;

public abstract class ServletMappingResolverFactory {

  protected abstract Collection<String> getMappings();

  public final MappingResolver create() {
    Collection<String> mappings = getMappings();
    if (mappings == null) {
      return null;
    }

    return MappingResolver.build(mappings);
  }
}
