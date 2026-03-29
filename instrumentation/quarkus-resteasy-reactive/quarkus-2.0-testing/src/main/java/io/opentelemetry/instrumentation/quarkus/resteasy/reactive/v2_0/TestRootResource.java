/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.quarkus.resteasy.reactive.v2_0;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("")
public class TestRootResource {

  @GET
  @Path("test")
  public String test() {
    return "success";
  }
}
