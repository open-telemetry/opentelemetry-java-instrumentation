/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4;

import com.alipay.sofa.rpc.core.request.SofaRequest;
import com.alipay.sofa.rpc.core.response.SofaResponse;
import com.alipay.sofa.rpc.ext.ExtensionLoader;
import com.alipay.sofa.rpc.ext.ExtensionLoaderFactory;
import com.alipay.sofa.rpc.filter.Filter;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import javax.annotation.Nullable;

/** Entrypoint for instrumenting SOFARPC clients or servers. */
public final class SofaRpcTelemetry {

  private static final String CLIENT_FILTER_NAME = "openTelemetryClient";

  private final Instrumenter<SofaRpcRequest, SofaResponse> serverInstrumenter;
  private final Instrumenter<SofaRpcRequest, SofaResponse> clientInstrumenter;

  /** Returns a new {@link SofaRpcTelemetry} configured with the given {@link OpenTelemetry}. */
  public static SofaRpcTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link SofaRpcTelemetryBuilder} configured with the given {@link OpenTelemetry}.
   */
  public static SofaRpcTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new SofaRpcTelemetryBuilder(openTelemetry);
  }

  SofaRpcTelemetry(
      Instrumenter<SofaRpcRequest, SofaResponse> serverInstrumenter,
      Instrumenter<SofaRpcRequest, SofaResponse> clientInstrumenter) {
    this.serverInstrumenter = serverInstrumenter;
    this.clientInstrumenter = clientInstrumenter;
  }

  /**
   * Completes telemetry for an asynchronous client request when a custom transport bypasses the
   * standard SOFARPC callback path.
   *
   * <p>This method must be called with the same {@link SofaRequest} instance that was passed to the
   * client filter. It safely does nothing when the request has no pending asynchronous telemetry or
   * has already been completed.
   */
  public static void completeAsyncResponse(
      SofaRequest request, @Nullable SofaResponse response, @Nullable Throwable exception) {
    ExtensionLoader<Filter> loader = ExtensionLoaderFactory.getExtensionLoader(Filter.class);
    if (loader.getExtensionClass(CLIENT_FILTER_NAME) == null) {
      TracingFilter.completeAsyncRequest(request, response, exception);
      return;
    }
    Filter filter = loader.getExtension(CLIENT_FILTER_NAME);
    filter.onAsyncResponse(null, request, response, exception);
  }

  /**
   * Returns a new {@link Filter} for use as a client-side SOFARPC filter that records telemetry for
   * outgoing RPC calls.
   */
  public Filter newClientFilter() {
    return new TracingFilter(clientInstrumenter, true);
  }

  /**
   * Returns a new {@link Filter} for use as a server-side SOFARPC filter that records telemetry for
   * incoming RPC calls.
   */
  public Filter newServerFilter() {
    return new TracingFilter(serverInstrumenter, false);
  }
}
