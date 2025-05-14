/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.v3_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.opensearch.rest.OpenSearchRestInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.opensearch.rest.OpenSearchRestRequest;
import io.opentelemetry.javaagent.instrumentation.opensearch.rest.OpenSearchRestResponse;
import java.net.InetAddress;
import org.opensearch.client.Response;

public final class OpenSearchRestSingletons {

  private static final Instrumenter<OpenSearchRestRequest, OpenSearchRestResponse> INSTRUMENTER =
      OpenSearchRestInstrumenterFactory.create("io.opentelemetry.opensearch-rest-3.0");

  public static Instrumenter<OpenSearchRestRequest, OpenSearchRestResponse> instrumenter() {
    return INSTRUMENTER;
  }

  public static OpenSearchRestResponse convertResponse(Response response) {
    return new OpenSearchRestResponse() {

      @Override
      public int getStatusCode() {
        return response.getStatusLine().getStatusCode();
      }

      @Override
      public InetAddress getAddress() {
        return response.getHost().getAddress();
      }
    };
  }

  private OpenSearchRestSingletons() {}
}
