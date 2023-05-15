/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.quarkus.resteasy.reactive.v3_0;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("")
public class TestRootResource {

  @GET
  @Path("test")
  public String test() {
    return "success";
  }
}
