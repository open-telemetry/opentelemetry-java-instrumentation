/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jaxrs.common.v2_0.test;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("test-resource-interface")
public interface JaxRsInterface {
  @Path("call")
  @GET
  Object call();
}
