/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import org.elasticsearch.common.settings.Settings
import org.elasticsearch.node.InternalSettingsPreparer
import org.elasticsearch.node.Node
import org.elasticsearch.transport.Netty4Plugin

class NodeFactory {
  static Node newNode(Settings settings) {
    def version = org.elasticsearch.Version.CURRENT
    if (version.major == 6 && version.minor >= 5) {
      return new NodeV65(settings)
    }
    return new NodeV6(settings)
  }

  static class NodeV6 extends Node {
    NodeV6(Settings settings) {
      super(InternalSettingsPreparer.prepareEnvironment(settings, null), [Netty4Plugin])
    }

    protected void registerDerivedNodeNameWithLogger(String s) {
    }
  }

  static class NodeV65 extends Node {
    NodeV65(Settings settings) {
      super(InternalSettingsPreparer.prepareEnvironment(settings, null), [Netty4Plugin], true)
    }

    protected void registerDerivedNodeNameWithLogger(String s) {
    }
  }
}
