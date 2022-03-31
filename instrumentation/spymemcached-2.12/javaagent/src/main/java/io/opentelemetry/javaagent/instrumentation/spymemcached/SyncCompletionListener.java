/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import static io.opentelemetry.javaagent.instrumentation.spymemcached.SpymemcachedSingletons.instrumenter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import net.spy.memcached.MemcachedConnection;

public class SyncCompletionListener extends CompletionListener<Void> {

  private static final Logger logger = Logger.getLogger(SyncCompletionListener.class.getName());

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
    logger.severe("processResult was called on SyncCompletionListener. This should never happen.");
  }

  public void done(Throwable thrown) {
    closeSyncSpan(thrown);
  }
}
