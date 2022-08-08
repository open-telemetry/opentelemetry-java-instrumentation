/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.opensearch.rest.OpenSearchRestInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.opensearch.rest.OpenSearchRestRequest;
import org.opensearch.client.Response;

public final class OpenSearchRest2Singletons {

  private static final Instrumenter<OpenSearchRestRequest, Response> INSTRUMENTER =
      OpenSearchRestInstrumenterFactory.create("io.opentelemetry.opensearch-rest-2.0");

  public static Instrumenter<OpenSearchRestRequest, Response> instrumenter() {
    return INSTRUMENTER;
  }

  private OpenSearchRest2Singletons() {}
}
