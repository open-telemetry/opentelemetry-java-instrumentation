/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.quarkus.resteasy.reactive.v2_0;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/hello")
@Produces(MediaType.TEXT_PLAIN)
public class GreetingResource {

  @GET
  @Path("/greeting/{name}")
  public String greeting(String name) {
    return "hello " + name;
  }

  @GET
  public String hello() {
    return "hello";
  }
}
