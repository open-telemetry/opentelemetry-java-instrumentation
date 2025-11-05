/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0.copied;

import io.opentelemetry.context.Context;

/**
 * {@link HttpServerResponseCustomizer} can be used to execute code after an HTTP server response is
 * created for the purpose of mutating the response in some way, such as appending headers, that may
 * depend on the context of the SERVER span.
 *
 * <p>This is a service provider interface that requires implementations to be registered in a
 * provider-configuration file stored in the {@code META-INF/services} resource directory.
 */
public interface HttpServerResponseCustomizer {
  /**
   * Called for each HTTP server response with its SERVER span context provided. This is called at a
   * time when response headers can already be set, but the response is not yet committed, which is
   * typically at the start of request handling.
   *
   * @param serverContext Context of a SERVER span {@link io.opentelemetry.api.trace.SpanContext}
   * @param response Response object specific to the library being instrumented
   * @param responseMutator Mutator through which the provided response object can be mutated
   */
  <RESPONSE> void customize(
      Context serverContext,
      RESPONSE response,
      HttpServerResponseMutator<RESPONSE> responseMutator);
}
