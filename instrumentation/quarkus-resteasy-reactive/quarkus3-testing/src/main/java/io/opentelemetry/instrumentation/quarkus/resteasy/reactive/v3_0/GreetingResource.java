/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.quarkus.resteasy.reactive.v3_0;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/hello")
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
