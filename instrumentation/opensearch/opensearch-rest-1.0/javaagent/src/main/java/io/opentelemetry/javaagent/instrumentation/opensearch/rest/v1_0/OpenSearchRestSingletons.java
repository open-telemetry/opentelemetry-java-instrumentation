/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.v1_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.OpenSearchRestInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.OpenSearchRestRequest;
import io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.OpenSearchRestResponse;
import java.net.InetAddress;
import javax.annotation.Nullable;
import org.opensearch.client.Response;

class OpenSearchRestSingletons {

  private static final Instrumenter<OpenSearchRestRequest, OpenSearchRestResponse> instrumenter =
      OpenSearchRestInstrumenterFactory.create("io.opentelemetry.opensearch-rest-1.0");

  static Instrumenter<OpenSearchRestRequest, OpenSearchRestResponse> instrumenter() {
    return instrumenter;
  }

  @Nullable
  static OpenSearchRestResponse convertResponse(@Nullable Response response) {
    if (response == null) {
      return null;
    }
    return new OpenSearchRestResponse() {

      @Override
      public int getStatusCode() {
        return response.getStatusLine().getStatusCode();
      }

      @Override
      @Nullable
      public InetAddress getAddress() {
        return response.getHost().getAddress();
      }
    };
  }

  private OpenSearchRestSingletons() {}
}
