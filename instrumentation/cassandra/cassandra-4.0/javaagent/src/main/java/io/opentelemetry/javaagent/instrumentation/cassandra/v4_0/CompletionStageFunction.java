/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import com.datastax.oss.driver.api.core.CqlSession;
import java.util.function.Function;
import javax.annotation.Nullable;

public class CompletionStageFunction implements Function<Object, Object> {

  @Override
  @Nullable
  public Object apply(@Nullable Object session) {
    if (session == null) {
      return null;
    }
    // This should cover ours and OT's TracingCqlSession
    if (session.getClass().getName().endsWith("cassandra4.TracingCqlSession")) {
      return session;
    }
    return TracingCqlSession.wrapSession((CqlSession) session);
  }
}
