/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.common.v2_7;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Result;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class DubboInstrumenters {

  private final Instrumenter<DubboRequest, Result> serverInstrumenter;
  private final Instrumenter<DubboRequest, Result> clientInstrumenter;

  DubboInstrumenters(
      Instrumenter<DubboRequest, Result> serverInstrumenter,
      Instrumenter<DubboRequest, Result> clientInstrumenter) {
    this.serverInstrumenter = serverInstrumenter;
    this.clientInstrumenter = clientInstrumenter;
  }

  /** Returns a new Dubbo client {@link Filter} that traces Dubbo RPC invocations. */
  public Filter newClientFilter() {
    return new TracingFilter(clientInstrumenter, true);
  }

  /** Returns a new Dubbo server {@link Filter} that traces Dubbo RPC invocations. */
  public Filter newServerFilter() {
    return new TracingFilter(serverInstrumenter, false);
  }

  /** Returns a server filter that can run before Dubbo initializes the service context. */
  public Filter newServerFilterWithInvoker() {
    return new TracingFilter(serverInstrumenter, false, true);
  }

  public Instrumenter<DubboRequest, Result> serverInstrumenter() {
    return serverInstrumenter;
  }
}
