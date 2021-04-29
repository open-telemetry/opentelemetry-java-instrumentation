/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.naming;

import java.util.Collection;

/** Helper class for finding mappings for a servlet or filter. */
public interface MappingProvider<SERVLETCONTEXT> {
  SERVLETCONTEXT getServletContext();

  /**
   * Get key for caching mapping info for servlet or filter associated with this provider.
   *
   * @return key for caching mapping info.
   */
  String getMappingKey();

  /**
   * Get mapping paths of servlet or filter associated with this provider.
   *
   * @return mapping paths of servlet or filter.
   */
  Collection<String> getMappings();
}
