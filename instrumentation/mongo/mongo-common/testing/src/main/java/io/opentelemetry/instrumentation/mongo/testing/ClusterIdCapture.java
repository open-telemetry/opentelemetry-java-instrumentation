/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.testing;

import static java.util.concurrent.TimeUnit.SECONDS;

import com.mongodb.connection.ClusterId;
import com.mongodb.event.ClusterClosedEvent;
import com.mongodb.event.ClusterDescriptionChangedEvent;
import com.mongodb.event.ClusterListener;
import com.mongodb.event.ClusterOpeningEvent;
import java.util.concurrent.CountDownLatch;

public class ClusterIdCapture implements ClusterListener {

  // since 4.6, the driver may publish cluster events from another thread
  private final CountDownLatch opened = new CountDownLatch(1);

  private volatile ClusterId clusterId;

  public ClusterId getClusterId() {
    try {
      if (!opened.await(10, SECONDS)) {
        throw new IllegalStateException("The driver did not open a cluster");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
    return clusterId;
  }

  @Override
  public void clusterOpening(ClusterOpeningEvent event) {
    clusterId = event.getClusterId();
    opened.countDown();
  }

  @Override
  public void clusterClosed(ClusterClosedEvent event) {}

  @Override
  public void clusterDescriptionChanged(ClusterDescriptionChangedEvent event) {}
}
