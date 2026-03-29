/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.quarkus.resteasy.reactive.v3_0;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("test-sub-resource-locator")
public class SubResourceLocatorTestResource {

  @Path("call")
  public Object call() {
    return new SubResource();
  }

  public static class SubResource {
    @Path("sub")
    @GET
    public String call() {
      return "success";
    }
  }
}
