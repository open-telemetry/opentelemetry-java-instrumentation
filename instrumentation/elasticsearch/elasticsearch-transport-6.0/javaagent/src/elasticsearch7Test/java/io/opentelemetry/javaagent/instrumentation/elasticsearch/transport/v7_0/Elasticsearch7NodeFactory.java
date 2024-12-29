/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v7_0;

import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v6_0.NodeFactory;
import java.util.Collections;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.InternalSettingsPreparer;
import org.elasticsearch.node.Node;
import org.elasticsearch.transport.Netty4Plugin;

class Elasticsearch7NodeFactory implements NodeFactory {
  @Override
  public Node newNode(Settings settings) {
    return new Node(
        InternalSettingsPreparer.prepareEnvironment(
            settings, Collections.emptyMap(), null, () -> "default node name"),
        Collections.singleton(Netty4Plugin.class),
        true) {};
  }
}
