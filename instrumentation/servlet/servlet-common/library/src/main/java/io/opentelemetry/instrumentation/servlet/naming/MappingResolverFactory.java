/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.naming;

import io.opentelemetry.instrumentation.api.servlet.MappingResolver;
import java.util.Collection;
import org.checkerframework.checker.nullness.qual.Nullable;

public abstract class MappingResolverFactory {

  private volatile MappingResolverHolder holder;

  @Nullable
  protected abstract Mappings getMappings();

  private MappingResolver build() {
    Mappings mappings = getMappings();
    if (mappings == null) {
      return null;
    }

    return MappingResolver.build(mappings.getMappings());
  }

  @Nullable
  public final MappingResolver get() {
    // build MappingResolver if it is not already built, no need to synchronize as it can safely be
    // built more than once
    if (holder == null) {
      holder = new MappingResolverHolder(build());
    }

    return holder.mappingResolver;
  }

  // using a holder class to distinguish build() returning null from build() not called
  private static class MappingResolverHolder {
    final MappingResolver mappingResolver;

    MappingResolverHolder(MappingResolver mappingResolver) {
      this.mappingResolver = mappingResolver;
    }
  }

  public static class Mappings {
    private final Collection<String> mappings;

    public Mappings(Collection<String> mappings) {
      this.mappings = mappings;
    }

    public Collection<String> getMappings() {
      return mappings;
    }
  }
}
