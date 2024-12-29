/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jaxrs.v2_0.test;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("test-resource-interface")
public interface JaxRsInterface {
  @Path("call")
  @GET
  Object call();
}
