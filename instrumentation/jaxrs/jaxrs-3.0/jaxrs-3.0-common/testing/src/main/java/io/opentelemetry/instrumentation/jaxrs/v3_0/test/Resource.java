/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jaxrs.v3_0.test;

import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;

@Path("/ignored")
public interface Resource {
  @Path("ignored")
  @Produces(TEXT_PLAIN)
  String hello(String name);

  @Path("/test")
  interface SubResource extends Cloneable, Resource {
    @Override
    @POST
    @Path("/hello/{name}")
    @Produces(TEXT_PLAIN)
    String hello(@PathParam("name") String name);
  }

  class Test1 implements SubResource {
    @Override
    public String hello(String name) {
      return "Test1 " + name + "!";
    }
  }

  @Path("/test2")
  class Test2 implements SubResource {
    @Override
    public String hello(String name) {
      return "Test2 " + name + "!";
    }
  }

  @Path("/test3")
  class Test3 implements SubResource {
    @Override
    @POST
    @Path("/hi/{name}")
    @Produces(TEXT_PLAIN)
    public String hello(@PathParam("name") String name) {
      return "Test3 " + name + "!";
    }

    @POST
    @Path("/nested")
    @Produces(TEXT_PLAIN)
    public String nested() {
      return hello("nested");
    }
  }
}
