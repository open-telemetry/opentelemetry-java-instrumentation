/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v6_5;

import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v6_0.AbstractElasticsearch6TransportClientTest;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v6_0.NodeFactory;

class Elasticsearch65TransportClientTest extends AbstractElasticsearch6TransportClientTest {

  @Override
  protected NodeFactory getNodeFactory() {
    return new Elasticsearch65NodeFactory();
  }
}
