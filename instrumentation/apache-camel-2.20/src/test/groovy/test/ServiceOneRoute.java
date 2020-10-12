/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test;

import org.apache.camel.builder.RouteBuilder;

public class ServiceOneRoute extends RouteBuilder {

  @Override
  public void configure() throws Exception {

    from("undertow:http://0.0.0.0:{{service.one.port}}/serviceOne")
        .routeId("serviceOne")
        .streamCaching()
        .removeHeaders("CamelHttp*")
        .log("Service One request: ${body}")
        .delay(simple("${random(1000,2000)}"))
        .transform(simple("Service-One-${body}"))
        .to("http://0.0.0.0:{{service.two.port}}/serviceTwo")
        .log("Service One response: ${body}");
  }
}
