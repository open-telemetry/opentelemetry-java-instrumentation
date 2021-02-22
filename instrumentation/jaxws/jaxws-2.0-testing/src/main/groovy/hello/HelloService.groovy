/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package hello

import javax.jws.WebParam
import javax.jws.WebResult
import javax.jws.WebService
import javax.xml.ws.RequestWrapper

@WebService(targetNamespace = "http://opentelemetry.io/test/hello-web-service")
interface HelloService {

  @RequestWrapper(localName = "helloRequest")
  @WebResult(name = "message")
  String hello(@WebParam(name = "name") String name)

  @RequestWrapper(localName = "hello2Request")
  @WebResult(name = "message")
  String hello2(@WebParam(name = "name") String name)

}