/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jaxrs.v3_0.test;

import jakarta.ws.rs.core.Application;
import java.util.HashSet;
import java.util.Set;

public class JaxRsTestApplication extends Application {
  @Override
  public Set<Class<?>> getClasses() {
    Set<Class<?>> classes = new HashSet<>();
    classes.add(JaxRsTestResource.class);
    classes.add(JaxRsSuperClassTestResource.class);
    classes.add(JaxRsInterfaceClassTestResource.class);
    classes.add(JaxRsSubResourceLocatorTestResource.class);
    classes.add(JaxRsTestExceptionMapper.class);
    return classes;
  }
}
