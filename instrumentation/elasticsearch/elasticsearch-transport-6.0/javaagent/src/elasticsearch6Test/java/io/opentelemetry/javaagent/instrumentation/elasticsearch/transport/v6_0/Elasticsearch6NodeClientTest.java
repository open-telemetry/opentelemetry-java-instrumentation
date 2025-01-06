/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v6_0;

class Elasticsearch6NodeClientTest extends AbstractElasticsearch6NodeClientTest {

  @Override
  protected NodeFactory getNodeFactory() {
    return new Elasticsearch6NodeFactory();
  }
}
