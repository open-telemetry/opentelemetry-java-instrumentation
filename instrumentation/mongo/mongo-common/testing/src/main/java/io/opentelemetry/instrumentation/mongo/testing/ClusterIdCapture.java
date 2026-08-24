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

/**
 * Captures the identity the driver gives the cluster it builds for a client, which is how a command
 * event finds the target that client was configured with.
 *
 * <p>Usable from driver 3.3 on, which is where the driver started letting a client settle a cluster
 * listener. From 4.6 on the driver hands cluster events to a listener of its own that republishes
 * them on a thread of its own, so the identity is waited for rather than read straight after the
 * client is built.
 */
public class ClusterIdCapture implements ClusterListener {

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
