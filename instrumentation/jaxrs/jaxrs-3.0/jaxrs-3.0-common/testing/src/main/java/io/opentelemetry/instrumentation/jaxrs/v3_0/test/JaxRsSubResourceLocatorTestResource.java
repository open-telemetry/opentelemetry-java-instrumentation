/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jaxrs.v3_0.test;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;

import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import jakarta.ws.rs.Path;

@Path("test-sub-resource-locator")
public class JaxRsSubResourceLocatorTestResource {
  @Path("call")
  public Object call() {
    return AbstractHttpServerTest.controller(SUCCESS, SubResource::new);
  }
}
