/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v6_0;

import java.util.Collections;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.InternalSettingsPreparer;
import org.elasticsearch.node.Node;
import org.elasticsearch.transport.Netty4Plugin;

class Elasticsearch6NodeFactory implements NodeFactory {
  @Override
  public Node newNode(Settings settings) {
    return new Node(
        InternalSettingsPreparer.prepareEnvironment(settings, null),
        Collections.singleton(Netty4Plugin.class)) {

      protected void registerDerivedNodeNameWithLogger(String s) {}
    };
  }
}
