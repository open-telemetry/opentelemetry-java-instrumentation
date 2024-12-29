/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jaxrs.v2_0.test;

import static io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest.controller;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;

public class JaxRsInterfaceClassTestResourceSuper {
  public Object call() {
    return controller(SUCCESS, SUCCESS::getBody);
  }
}
