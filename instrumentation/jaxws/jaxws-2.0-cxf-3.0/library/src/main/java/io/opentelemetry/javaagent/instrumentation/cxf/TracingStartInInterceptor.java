/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cxf;

import static io.opentelemetry.javaagent.instrumentation.cxf.CxfJaxWsTracer.tracer;

import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

public class TracingStartInInterceptor extends AbstractPhaseInterceptor {

  public TracingStartInInterceptor() {
    super(Phase.PRE_INVOKE);
  }

  @Override
  public void handleMessage(Message message) {
    tracer().startSpan(message);
  }
}
