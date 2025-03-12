/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class EmittedMetricsTest {
  @Test
  public void testDeserialization() throws IOException {
    String yamlContent =
        """
            metrics:
              - name: db.client.connections.usage
                description: The number of connections that are currently in state described by the state attribute.
                type: LONG_SUM
                unit: connections
                attributes:
                  - name: pool.name
                    type: STRING
                  - name: state
                    type: STRING
              - name: db.client.connections.pending_requests
                description: The number of pending requests for an open connection, cumulative for the entire pool.
                type: LONG_SUM
                unit: requests
                attributes:
                  - name: pool.name
                    type: STRING
              - name: db.client.connections.max
                description: The maximum number of open connections allowed.
                type: LONG_SUM
                unit: connections
                attributes:
                  - name: pool.name
                    type: STRING
              - name: db.client.connections.idle.min
                description: The minimum number of idle open connections allowed.
                type: LONG_SUM
                unit: connections
                attributes:
                  - name: pool.name
                    type: STRING
              - name: db.client.connections.idle.max
                description: The maximum number of idle open connections allowed.
                type: LONG_SUM
                unit: connections
                attributes:
                  - name: pool.name
                    type: STRING
            """;

    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    EmittedMetrics emittedMetrics = mapper.readValue(yamlContent, EmittedMetrics.class);

    assertThat(emittedMetrics).isNotNull();
    assertThat(emittedMetrics.getMetrics().size()).isEqualTo(5);
  }
}
