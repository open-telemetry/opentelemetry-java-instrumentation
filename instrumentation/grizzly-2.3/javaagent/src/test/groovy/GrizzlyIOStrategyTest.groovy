/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import org.glassfish.grizzly.IOStrategy
import org.glassfish.grizzly.http.server.NetworkListener
import org.glassfish.grizzly.strategies.LeaderFollowerNIOStrategy
import org.glassfish.grizzly.strategies.SameThreadIOStrategy
import org.glassfish.grizzly.strategies.SimpleDynamicNIOStrategy

abstract class GrizzlyIOStrategyTest extends GrizzlyTest {

  @Override
  void configureListener(NetworkListener listener) {
    // Default in NIOTransportBuilder is WorkerThreadIOStrategy, so don't need to retest that.
    listener.getTransport().setIOStrategy(strategy())
  }

  abstract IOStrategy strategy()
}

class LeaderFollowerTest extends GrizzlyIOStrategyTest {

  @Override
  IOStrategy strategy() {
    return LeaderFollowerNIOStrategy.instance
  }
}

class SameThreadTest extends GrizzlyIOStrategyTest {

  @Override
  IOStrategy strategy() {
    return SameThreadIOStrategy.instance
  }
}

class SimpleDynamicTest extends GrizzlyIOStrategyTest {

  @Override
  IOStrategy strategy() {
    return SimpleDynamicNIOStrategy.instance
  }
}
