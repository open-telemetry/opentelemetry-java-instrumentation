/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test;

import org.apache.camel.builder.RouteBuilder;

public class ServiceTwoRoute extends RouteBuilder {

  @Override
  public void configure() throws Exception {

    from("jetty:http://0.0.0.0:{{service.two.port}}/serviceTwo")
        .routeId("serviceTwo")
        .streamCaching()
        .log("Service Two request: ${body}")
        .delay(simple("${random(1000, 2000)}"))
        .transform(simple("Service-Two-${body}"))
        .log("Service Two response: ${body}");
  }
}
