/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import org.glassfish.grizzly.IOStrategy;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.strategies.LeaderFollowerNIOStrategy;
import org.glassfish.grizzly.strategies.SameThreadIOStrategy;
import org.glassfish.grizzly.strategies.SimpleDynamicNIOStrategy;

abstract class GrizzlyIOStrategyTest extends GrizzlyTest {

  @Override
  public void configureListener(NetworkListener listener) {
    // Default in NIOTransportBuilder is WorkerThreadIOStrategy, so don't need to retest that.
    listener.getTransport().setIOStrategy(strategy());
  }

  abstract IOStrategy strategy();
}

class LeaderFollowerTest extends GrizzlyIOStrategyTest {

  @Override
  IOStrategy strategy() {
    return LeaderFollowerNIOStrategy.getInstance();
  }
}

class SameThreadTest extends GrizzlyIOStrategyTest {

  @Override
  IOStrategy strategy() {
    return SameThreadIOStrategy.getInstance();
  }
}

class SimpleDynamicTest extends GrizzlyIOStrategyTest {

  @Override
  IOStrategy strategy() {
    return SimpleDynamicNIOStrategy.getInstance();
  }
}
