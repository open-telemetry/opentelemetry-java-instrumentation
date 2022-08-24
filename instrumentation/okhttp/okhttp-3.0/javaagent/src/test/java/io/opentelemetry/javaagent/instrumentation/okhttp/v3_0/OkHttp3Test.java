/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v3_0;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.okhttp.v3_0.AbstractOkHttp3Test;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class OkHttp3Test extends AbstractOkHttp3Test {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  @Override
  public Call.Factory createCallFactory(OkHttpClient.Builder clientBuilder) {
    return clientBuilder.build();
  }

  @Test
  void reusedBuilderHasOneInterceptor() {
    OkHttpClient.Builder builder =
        new OkHttpClient.Builder()
            .connectTimeout(CONNECTION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false);

    OkHttpClient newClient = builder.build().newBuilder().build();

    assertThat(newClient.interceptors()).hasSize(1);
  }

  @Test
  void builderCreatedFromClientHasOneInterceptor() {
    OkHttpClient newClient = ((OkHttpClient) client).newBuilder().build();

    assertThat(newClient.interceptors()).hasSize(1);
  }
}
