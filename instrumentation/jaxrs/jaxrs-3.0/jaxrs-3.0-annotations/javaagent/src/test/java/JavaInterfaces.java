/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

public class JavaInterfaces {

  interface Jax {

    void call();
  }

  @Path("interface")
  interface InterfaceWithClassMethodPath extends Jax {

    @Override
    @GET
    @Path("invoke")
    void call();
  }

  @Path("abstract")
  abstract static class AbstractClassOnInterfaceWithClassPath
      implements InterfaceWithClassMethodPath {

    @GET
    @Path("call")
    @Override
    public void call() {
      // do nothing
    }

    abstract void actual();
  }

  @Path("child")
  static class ChildClassOnInterface extends AbstractClassOnInterfaceWithClassPath {

    @Override
    void actual() {
      // do nothing
    }
  }

  @Path("interface")
  interface DefaultInterfaceWithClassMethodPath extends Jax {

    @Override
    @GET
    @Path("call")
    default void call() {
      actual();
    }

    void actual();
  }

  @Path("child")
  static class DefaultChildClassOnInterface implements DefaultInterfaceWithClassMethodPath {

    @Override
    public void actual() {
      // do nothing
    }
  }
}
