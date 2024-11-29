/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jaxrs.v3_0.test;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;

import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import jakarta.ws.rs.GET;

public class JaxRsSuperClassTestResourceSuper {
  @GET
  public Object call() {
    return AbstractHttpServerTest.controller(SUCCESS, SUCCESS::getBody);
  }
}
