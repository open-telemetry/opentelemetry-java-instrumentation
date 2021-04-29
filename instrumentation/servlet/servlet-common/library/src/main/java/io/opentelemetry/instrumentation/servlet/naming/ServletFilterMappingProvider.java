/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.naming;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class ServletFilterMappingProvider<SERVLETCONTEXT, FILTERREGISTRATION>
    implements MappingProvider<SERVLETCONTEXT> {
  protected final SERVLETCONTEXT servletContext;
  protected final String filterName;

  public ServletFilterMappingProvider(SERVLETCONTEXT servletContext, String filterName) {
    this.servletContext = servletContext;
    this.filterName = filterName;
  }

  @Override
  public SERVLETCONTEXT getServletContext() {
    return servletContext;
  }

  @Override
  public String getMappingKey() {
    return "filter." + filterName;
  }

  public abstract FILTERREGISTRATION getFilterRegistration();

  public abstract Collection<String> getUrlPatternMappings(FILTERREGISTRATION filterRegistration);

  public abstract Collection<String> getServletNameMappings(FILTERREGISTRATION filterRegistration);

  public abstract Collection<String> getServletMappings(String servletName);

  @Override
  public Collection<String> getMappings() {
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
    Collections.sort(mappingsList, (s1, s2) -> s2.length() - s1.length());

    return mappingsList;
  }
}
