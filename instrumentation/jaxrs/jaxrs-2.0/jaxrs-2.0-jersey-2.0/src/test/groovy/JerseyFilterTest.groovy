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

import io.dropwizard.testing.junit.ResourceTestRule
import javax.ws.rs.client.Entity
import javax.ws.rs.core.Response
import org.junit.ClassRule
import spock.lang.Shared

class JerseyFilterTest extends JaxRsFilterTest {
  @Shared
  @ClassRule
  ResourceTestRule resources = ResourceTestRule.builder()
    .addResource(new Test1())
    .addResource(new Test2())
    .addResource(new Test3())
    .addProvider(simpleRequestFilter)
    .addProvider(prematchRequestFilter)
    .build()

  @Override
  def makeRequest(String url) {
    Response response = resources.client().target(url).request().post(Entity.text(""))

    return [response.readEntity(String), response.statusInfo.statusCode]
  }
}