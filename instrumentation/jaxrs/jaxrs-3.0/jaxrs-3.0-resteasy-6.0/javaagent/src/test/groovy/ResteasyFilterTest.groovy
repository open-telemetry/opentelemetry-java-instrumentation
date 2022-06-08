/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import jakarta.ws.rs.core.MediaType
import org.jboss.resteasy.mock.MockDispatcherFactory
import org.jboss.resteasy.mock.MockHttpRequest
import org.jboss.resteasy.mock.MockHttpResponse
import spock.lang.Shared

import static Resource.Test1
import static Resource.Test2
import static Resource.Test3

class ResteasyFilterTest extends JaxRsFilterTest {
  @Shared
  def dispatcher

  def setupSpec() {
    dispatcher = MockDispatcherFactory.createDispatcher()
    def registry = dispatcher.getRegistry()
    registry.addSingletonResource(new Test1())
    registry.addSingletonResource(new Test2())
    registry.addSingletonResource(new Test3())

    dispatcher.getProviderFactory().register(simpleRequestFilter)
    dispatcher.getProviderFactory().register(prematchRequestFilter)
  }

  @Override
  def makeRequest(String url) {
    MockHttpRequest request = MockHttpRequest.post(url)
    request.contentType(MediaType.TEXT_PLAIN_TYPE)
    request.content(new byte[0])

    MockHttpResponse response = new MockHttpResponse()
    dispatcher.invoke(request, response)

    return [response.contentAsString, response.status]
  }

}