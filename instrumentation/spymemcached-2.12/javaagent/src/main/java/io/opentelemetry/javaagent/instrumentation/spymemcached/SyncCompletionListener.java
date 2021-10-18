/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import static io.opentelemetry.javaagent.instrumentation.spymemcached.SpymemcachedSingletons.instrumenter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import javax.annotation.Nullable;
import net.spy.memcached.MemcachedConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyncCompletionListener extends CompletionListener<Void> {

  private static final Logger logger = LoggerFactory.getLogger(SyncCompletionListener.class);

  private SyncCompletionListener(Context parentContext, SpymemcachedRequest request) {
    super(parentContext, request);
  }

  @Nullable
  public static SyncCompletionListener create(
      Context parentContext, MemcachedConnection connection, String methodName) {
    SpymemcachedRequest request = SpymemcachedRequest.create(connection, methodName);
    if (!instrumenter().shouldStart(parentContext, request)) {
      return null;
    }
    return new SyncCompletionListener(parentContext, request);
  }

  @Override
  protected void processResult(Span span, Void future) {
    logger.error("processResult was called on SyncCompletionListener. This should never happen. ");
  }

  public void done(Throwable thrown) {
    closeSyncSpan(thrown);
  }
}
