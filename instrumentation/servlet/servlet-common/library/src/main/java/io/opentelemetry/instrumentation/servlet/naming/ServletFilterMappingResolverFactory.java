/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.naming;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;

public abstract class ServletFilterMappingResolverFactory<FILTERREGISTRATION>
    extends ServletMappingResolverFactory {

  protected abstract FILTERREGISTRATION getFilterRegistration();

  protected abstract Collection<String> getUrlPatternMappings(
      FILTERREGISTRATION filterRegistration);

  protected abstract Collection<String> getServletNameMappings(
      FILTERREGISTRATION filterRegistration);

  protected abstract Collection<String> getServletMappings(String servletName);

  @Override
  @Nullable
  protected Mappings getMappings() {
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
    // sort the longest mapping first
    mappingsList.sort((s1, s2) -> s2.length() - s1.length());

    return new Mappings(mappingsList);
  }
}
