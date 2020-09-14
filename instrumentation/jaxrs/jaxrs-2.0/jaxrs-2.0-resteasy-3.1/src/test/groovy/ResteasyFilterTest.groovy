/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import static Resource.Test1
import static Resource.Test2
import static Resource.Test3

import javax.ws.rs.core.MediaType
import org.jboss.resteasy.mock.MockDispatcherFactory
import org.jboss.resteasy.mock.MockHttpRequest
import org.jboss.resteasy.mock.MockHttpResponse
import spock.lang.Shared

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