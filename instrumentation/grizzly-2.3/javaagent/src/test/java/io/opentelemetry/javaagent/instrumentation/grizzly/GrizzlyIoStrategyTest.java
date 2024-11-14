/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import org.glassfish.grizzly.IOStrategy;
import org.glassfish.grizzly.http.server.NetworkListener;

abstract class GrizzlyIoStrategyTest extends GrizzlyTest {

  @Override
  public void configureListener(NetworkListener listener) {
    // Default in NIOTransportBuilder is WorkerThreadIOStrategy, so don't need to retest that.
    listener.getTransport().setIOStrategy(strategy());
  }

  abstract IOStrategy strategy();
}
