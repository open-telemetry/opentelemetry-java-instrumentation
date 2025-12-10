/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.gateway.v2_0;

import io.opentelemetry.instrumentation.spring.gateway.common.AbstractRouteMappingTest;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.List;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {GatewayTestApplication.class})
class GatewayRouteMappingTest extends AbstractRouteMappingTest {

  @Override
  protected String getSpanName() {
    return "POST path_route";
  }

  @Override
  protected List<AttributeAssertion> getExpectedAttributes() {
    return buildAttributeAssertions("path_route", "h1c://mock.response", 0, 1);
  }

  @Override
  protected List<AttributeAssertion> getRandomUuidExpectedAttributes() {
    return buildAttributeAssertions("h1c://mock.uuid", 0, 1);
  }

  @Override
  protected List<AttributeAssertion> getFakeUuidExpectedAttributes(String routeId) {
    return buildAttributeAssertions(routeId, "h1c://mock.fake", 0, 1);
  }
}
