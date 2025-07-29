/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import org.glassfish.grizzly.IOStrategy;
import org.glassfish.grizzly.strategies.SimpleDynamicNIOStrategy;

class SimpleDynamicTest extends GrizzlyIoStrategyTest {

  @Override
  IOStrategy strategy() {
    return SimpleDynamicNIOStrategy.getInstance();
  }
}
