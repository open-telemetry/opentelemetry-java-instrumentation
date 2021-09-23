/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.dropwizard.testing.junit.ResourceTestRule
import org.junit.ClassRule
import spock.lang.Shared

import javax.ws.rs.client.Entity
import javax.ws.rs.core.Response

import static Resource.Test1
import static Resource.Test2
import static Resource.Test3

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