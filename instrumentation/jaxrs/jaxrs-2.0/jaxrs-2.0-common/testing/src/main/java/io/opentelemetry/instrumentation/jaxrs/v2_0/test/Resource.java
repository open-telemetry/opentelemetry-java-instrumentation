/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jaxrs.v2_0.test;

import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

@Path("/ignored")
public interface Resource {
  @Path("ignored")
  String hello(String name);

  @Path("/test")
  interface SubResource extends Cloneable, Resource {
    @Override
    @POST
    @Path("/hello/{name}")
    String hello(@PathParam("name") String name);
  }

  class Test1 implements SubResource {
    @Override
    @Produces(TEXT_PLAIN)
    public String hello(String name) {
      return "Test1 " + name + "!";
    }
  }

  @Path("/test2")
  class Test2 implements SubResource {
    @Override
    @Produces(TEXT_PLAIN)
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
