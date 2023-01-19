/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v2_2;

import static io.opentelemetry.instrumentation.testing.junit.http.HttpClientTests.CONNECTION_TIMEOUT_MS;
import static io.opentelemetry.instrumentation.testing.junit.http.HttpClientTests.READ_TIMEOUT_MS;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptionsBuilder;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTests;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTypeAdapter;
import io.opentelemetry.instrumentation.testing.junit.http.LegacyHttpClientTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.NewHttpClientInstrumentationExtension;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.RegisterExtension;

public class OkHttp2Test {
  @RegisterExtension
  static final NewHttpClientInstrumentationExtension testing =
      NewHttpClientInstrumentationExtension.forAgent();

  @TestFactory
  Collection<DynamicTest> test() {
    HttpClientTestOptions options = buildOptions();
    HttpClientTypeAdapter<Request> adapter = buildTypeAdapter();
    HttpClientTests<Request> tests =
        new HttpClientTests<>(testing.getTestRunner(), testing.getServer(), options, adapter);
    return tests.allList();
  }

  private HttpClientTestOptions buildOptions() {
    HttpClientTestOptionsBuilder options = HttpClientTestOptions.builder();
    options.disableTestCircularRedirects();
    options.enableTestReadTimeout();
    options.httpAttributes(
        uri -> {
          Set<AttributeKey<?>> attributes =
              new HashSet<>(LegacyHttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES);

          // flavor is extracted from the response, and those URLs cause exceptions (= null
          // response)
          String serverAddress = testing.getServer().resolveAddress("/read-timeout").toString();
          if ("http://localhost:61/".equals(uri.toString())
              || "https://192.0.2.1/".equals(uri.toString())
              || serverAddress.equals(uri.toString())) {
            attributes.remove(SemanticAttributes.HTTP_FLAVOR);
          }

          return attributes;
        });
    return options.build();
  }

  @NotNull
  private static HttpClientTypeAdapter<Request> buildTypeAdapter() {
    OkHttpClient client = new OkHttpClient();
    OkHttpClient clientWithReadTimeout = new OkHttpClient();

    client.setConnectTimeout(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    clientWithReadTimeout.setConnectTimeout(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    clientWithReadTimeout.setReadTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    return new OkHttp2TypeAdapter(client, clientWithReadTimeout);
  }
}
