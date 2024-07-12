/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v3_0;

import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.okhttp.v3_0.AbstractOkHttp3Test;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import org.junit.jupiter.api.extension.RegisterExtension;

class OkHttp3Http2Test extends AbstractOkHttp3Test {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  @Override
  public Call.Factory createCallFactory(OkHttpClient.Builder clientBuilder) {
    clientBuilder.protocols(singletonList(Protocol.H2_PRIOR_KNOWLEDGE));
    return clientBuilder.build();
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    super.configure(optionsBuilder);

    // https does not work with H2_PRIOR_KNOWLEDGE
    optionsBuilder.disableTestHttps();
    optionsBuilder.setHttpProtocolVersion(uri -> "2");
  }
}
