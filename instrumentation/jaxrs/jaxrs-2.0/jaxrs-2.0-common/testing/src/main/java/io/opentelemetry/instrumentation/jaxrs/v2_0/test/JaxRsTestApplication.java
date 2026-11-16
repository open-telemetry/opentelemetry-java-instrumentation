/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jaxrs.v2_0.test;

import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
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
    classes.add(CovariantInterfaceResource.class);
    classes.add(CovariantSuperclassResource.class);
    return classes;
  }

  public interface CovariantResource<T> {
    @GET
    @Path("current")
    T get();
  }

  @Path("covariant-interface")
  public static class CovariantInterfaceResource implements CovariantResource<String> {
    @Override
    public String get() {
      return "item";
    }
  }

  public abstract static class AbstractCovariantResource {
    @GET
    @Path("current")
    public abstract Object get();
  }

  @Path("covariant-superclass")
  public static class CovariantSuperclassResource extends AbstractCovariantResource {
    @Override
    public String get() {
      return "item";
    }
  }
}
