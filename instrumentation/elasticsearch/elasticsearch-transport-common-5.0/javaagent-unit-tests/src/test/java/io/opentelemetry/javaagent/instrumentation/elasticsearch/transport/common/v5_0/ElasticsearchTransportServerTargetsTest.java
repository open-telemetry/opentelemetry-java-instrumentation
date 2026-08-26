/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0.ElasticsearchTransportServerTarget.Endpoint;
import org.elasticsearch.client.support.AbstractClient;
import org.junit.jupiter.api.Test;

class ElasticsearchTransportServerTargetsTest {

  @Test
  void captureIsIdempotent() {
    AbstractClient client = mock(AbstractClient.class);

    ElasticsearchTransportServerTargets.capture(
        client, singletonList(new Endpoint("10.0.0.1", 9300)));
    ElasticsearchTransportServerTargets.capture(
        client, singletonList(new Endpoint("10.0.0.2", 9301)));

    assertThat(ElasticsearchTransportServerTargets.address(client)).isEqualTo("10.0.0.1");
    assertThat(ElasticsearchTransportServerTargets.port(client)).isEqualTo(9300);
  }
}
