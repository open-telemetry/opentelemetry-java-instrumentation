/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test;

import org.apache.camel.builder.RouteBuilder;

public class SingleServiceRoute extends RouteBuilder {

  @Override
  public void configure() throws Exception {

    from("undertow:http://0.0.0.0:{{camelService.port}}/camelService")
        .routeId("camelService")
        .streamCaching()
        .log(" CamelService request: ${body}")
        .delay(simple("${random(1000, 2000)}"))
        .transform(simple("CamelService-${body}"))
        .log("CamelService response: ${body}");
  }
}
