/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jaxrs.v3_0.test;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("test-resource-interface")
public interface JaxRsInterface {
  @Path("call")
  @GET
  Object call();
}
