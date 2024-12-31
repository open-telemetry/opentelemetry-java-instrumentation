/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.v3_0.hello;

import jakarta.jws.WebParam;
import jakarta.jws.WebResult;
import jakarta.jws.WebService;
import jakarta.xml.ws.RequestWrapper;

@WebService(targetNamespace = "http://opentelemetry.io/test/hello-web-service")
public interface HelloService {

  @RequestWrapper(localName = "helloRequest")
  @WebResult(name = "message")
  String hello(@WebParam(name = "name") String name);
}
