/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.xxljob;

import static java.util.logging.Level.FINE;

import io.opentelemetry.context.Context;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

/**
 * A thread-safe queue for managing OpenTelemetry contexts.
 *
 * <p>This class uses a {@link LinkedBlockingQueue} to store and retrieve contexts, ensuring
 * thread-safe operations when propagating contexts across thread boundaries.
 */
public final class ContextQueue {

  private static final Logger logger = Logger.getLogger(ContextQueue.class.getName());

  private final LinkedBlockingQueue<Context> queue = new LinkedBlockingQueue<>();

  public ContextQueue() {}

  public void offer(Context context) {
    try {
      queue.offer(context);
    } catch (Throwable e) {
      if (logger.isLoggable(FINE)) {
        logger.log(FINE, "Failed to offer context to queue: " + context, e);
      }
    }
  }

  public Context poll() {
    try {
      return queue.poll();
    } catch (Throwable e) {
      if (logger.isLoggable(FINE)) {
        logger.log(FINE, "Failed to poll context from queue", e);
      }
      return null;
    }
  }

  public void clear() {
    try {
      queue.clear();
    } catch (Throwable e) {
      if (logger.isLoggable(FINE)) {
        logger.log(FINE, "Failed to clear queue", e);
      }
    }
  }

  public int size() {
    try {
      return queue.size();
    } catch (Throwable e) {
      if (logger.isLoggable(FINE)) {
        logger.log(FINE, "Failed to get queue size", e);
      }
      return 0;
    }
  }
}
