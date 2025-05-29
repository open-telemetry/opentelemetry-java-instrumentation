/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v6_0;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.Node;

public interface NodeFactory {

  Node newNode(Settings settings);
}
