/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import io.opentelemetry.trace.Span;
import net.spy.memcached.MemcachedConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyncCompletionListener extends CompletionListener<Void> {

  private static final Logger log = LoggerFactory.getLogger(SyncCompletionListener.class);

  public SyncCompletionListener(MemcachedConnection connection, String methodName) {
    super(connection, methodName);
  }

  @Override
  protected void processResult(Span span, Void future) {
    log.error("processResult was called on SyncCompletionListener. This should never happen. ");
  }

  public void done(Throwable thrown) {
    closeSyncSpan(thrown);
  }
}
