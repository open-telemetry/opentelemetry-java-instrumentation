/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.boot

import io.opentelemetry.test.hello_web_service.HelloRequest
import io.opentelemetry.test.hello_web_service.HelloRequestSoapAction
import io.opentelemetry.test.hello_web_service.HelloRequestWsAction
import io.opentelemetry.test.hello_web_service.HelloResponse
import org.springframework.ws.server.endpoint.annotation.Endpoint
import org.springframework.ws.server.endpoint.annotation.PayloadRoot
import org.springframework.ws.server.endpoint.annotation.RequestPayload
import org.springframework.ws.server.endpoint.annotation.ResponsePayload
import org.springframework.ws.soap.addressing.server.annotation.Action
import org.springframework.ws.soap.server.endpoint.annotation.SoapAction

@Endpoint
class HelloEndpoint {
  private static final String NAMESPACE_URI = "http://opentelemetry.io/test/hello-web-service"

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "helloRequest")
  @ResponsePayload
  HelloResponse hello(@RequestPayload HelloRequest request) {
    return handleHello(request.getName())
  }

  @SoapAction(value = "http://opentelemetry.io/test/hello-soap-action")
  @ResponsePayload
  HelloResponse helloSoapAction(@RequestPayload HelloRequestSoapAction request) {
    return handleHello(request.getName())
  }

  @Action(value = "http://opentelemetry.io/test/hello-ws-action")
  @ResponsePayload
  HelloResponse helloWsAction(@RequestPayload HelloRequestWsAction request) {
    return handleHello(request.getName())
  }

  private HelloResponse handleHello(String name) {
    if ("exception" == name) {
      throw new Exception("hello exception")
    }
    HelloResponse response = new HelloResponse()
    response.setMessage("Hello " + name)

    return response
  }
}
