/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jaxrs.v2_0.test;

import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.core.Application;

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
