/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v7_0;

import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v6_0.AbstractElasticsearch6NodeClientTest;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v6_0.NodeFactory;

class Elasticsearch7NodeClientTest extends AbstractElasticsearch6NodeClientTest {

  @Override
  protected NodeFactory getNodeFactory() {
    return new Elasticsearch7NodeFactory();
  }

  @Override
  protected String getIndexNotFoundMessage() {
    return "invalid-index";
  }
}
