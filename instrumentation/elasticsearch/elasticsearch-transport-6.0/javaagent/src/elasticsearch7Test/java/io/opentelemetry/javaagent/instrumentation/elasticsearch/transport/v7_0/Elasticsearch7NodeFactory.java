/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v7_0;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;

import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v6_0.NodeFactory;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.InternalSettingsPreparer;
import org.elasticsearch.node.Node;
import org.elasticsearch.transport.Netty4Plugin;

class Elasticsearch7NodeFactory implements NodeFactory {
  @Override
  public Node newNode(Settings settings) {
    return new Node(
        InternalSettingsPreparer.prepareEnvironment(
            settings, emptyMap(), null, () -> "default node name"),
        singleton(Netty4Plugin.class),
        true) {};
  }
}
