/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.quarkus.resteasy.reactive.v2_0;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

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
