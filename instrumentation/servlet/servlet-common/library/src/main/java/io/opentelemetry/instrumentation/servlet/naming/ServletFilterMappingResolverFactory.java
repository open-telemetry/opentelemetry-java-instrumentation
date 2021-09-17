/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.naming;

import io.opentelemetry.instrumentation.api.servlet.MappingResolver;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public abstract class ServletFilterMappingResolverFactory<FILTERREGISTRATION>
    implements Supplier<MappingResolver> {

  protected abstract FILTERREGISTRATION getFilterRegistration();

  protected abstract Collection<String> getUrlPatternMappings(
      FILTERREGISTRATION filterRegistration);

  protected abstract Collection<String> getServletNameMappings(
      FILTERREGISTRATION filterRegistration);

  protected abstract Collection<String> getServletMappings(String servletName);

  // TODO(anuraaga): We currently treat null as no mappings, and empty as having a default mapping.
  // Error prone is correctly flagging this behavior as error prone.
  @SuppressWarnings("ReturnsNullCollection")
  private Collection<String> getMappings() {
    FILTERREGISTRATION filterRegistration = getFilterRegistration();
    if (filterRegistration == null) {
      return null;
    }
    Set<String> mappings = new HashSet<>();
    Collection<String> urlPatternMappings = getUrlPatternMappings(filterRegistration);
    if (urlPatternMappings != null) {
      mappings.addAll(urlPatternMappings);
    }
    Collection<String> servletNameMappings = getServletNameMappings(filterRegistration);
    if (servletNameMappings != null) {
      for (String servletName : servletNameMappings) {
        Collection<String> servletMappings = getServletMappings(servletName);
        if (servletMappings != null) {
          mappings.addAll(servletMappings);
        }
      }
    }

    if (mappings.isEmpty()) {
      return null;
    }

    List<String> mappingsList = new ArrayList<>(mappings);
    // sort longest mapping first
    mappingsList.sort((s1, s2) -> s2.length() - s1.length());

    return mappingsList;
  }

  @Override
  public final MappingResolver get() {
    Collection<String> mappings = getMappings();
    if (mappings == null) {
      return null;
    }

    return MappingResolver.build(mappings);
  }
}
