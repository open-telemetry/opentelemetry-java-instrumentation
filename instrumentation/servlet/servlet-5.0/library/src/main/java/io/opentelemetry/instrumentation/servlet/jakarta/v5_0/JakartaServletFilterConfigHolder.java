/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.jakarta.v5_0;

import io.opentelemetry.instrumentation.api.caching.Cache;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterConfig;

/** Helper class for keeping track of FilterConfiguration instance passed to Filter init method. */
public final class JakartaServletFilterConfigHolder {
  private static final Cache<Filter, FilterConfig> filterConfigs =
      Cache.newBuilder().setWeakKeys().build();

  public static FilterConfig getFilterConfig(Filter filter) {
    return filterConfigs.get(filter);
  }

  public static void setFilterConfig(Filter filter, FilterConfig filterConfig) {
    filterConfigs.put(filter, filterConfig);
  }

  private JakartaServletFilterConfigHolder() {}
}
