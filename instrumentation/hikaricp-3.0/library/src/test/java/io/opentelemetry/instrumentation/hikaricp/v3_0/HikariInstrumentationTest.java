/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.hikaricp.v3_0;

import static org.mockito.Mockito.mock;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.metrics.IMetricsTracker;
import com.zaxxer.hikari.metrics.MetricsTrackerFactory;
import com.zaxxer.hikari.metrics.PoolStats;
import io.opentelemetry.instrumentation.hikaricp.AbstractHikariInstrumentationTest;
import io.opentelemetry.instrumentation.test.utils.GcUtils;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class HikariInstrumentationTest extends AbstractHikariInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  // Keep the tracker reachable after close, as a closed data source would.
  private IMetricsTracker retainedTracker;

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected void configure(HikariConfig poolConfig, MetricsTrackerFactory userTracker) {
    poolConfig.setMetricsTrackerFactory(
        HikariTelemetry.create(testing().getOpenTelemetry())
            .createMetricsTrackerFactory(userTracker));
  }

  @Test
  void releasesRegistrationClassLoaderOnTrackerClose()
      throws InterruptedException, TimeoutException {
    MetricsTrackerFactory factory =
        HikariTelemetry.create(testing.getOpenTelemetry()).createMetricsTrackerFactory();
    PoolStats poolStats = mock(PoolStats.class);
    Thread thread = Thread.currentThread();
    ClassLoader previousLoader = thread.getContextClassLoader();
    ClassLoader registrationLoader = new ClassLoader(previousLoader) {};
    WeakReference<ClassLoader> loaderRef = new WeakReference<>(registrationLoader);
    thread.setContextClassLoader(registrationLoader);
    try {
      retainedTracker = factory.create("testPool", poolStats);
    } finally {
      thread.setContextClassLoader(previousLoader);
    }
    registrationLoader = null;

    retainedTracker.close();

    GcUtils.awaitGc(loaderRef, Duration.ofSeconds(10));
  }
}
