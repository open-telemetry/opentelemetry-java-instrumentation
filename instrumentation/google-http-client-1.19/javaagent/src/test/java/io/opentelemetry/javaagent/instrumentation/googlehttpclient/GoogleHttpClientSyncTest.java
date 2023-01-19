/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.googlehttpclient;

import com.google.api.client.http.HttpRequest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTypeAdapter;
import io.opentelemetry.instrumentation.testing.junit.http.NewHttpClientInstrumentationExtension;
import java.util.Collection;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.RegisterExtension;

class GoogleHttpClientSyncTest {

  @RegisterExtension
  static final NewHttpClientInstrumentationExtension testing =
      NewHttpClientInstrumentationExtension.forAgent();

  @TestFactory
  Collection<DynamicTest> test() {
    HttpClientTypeAdapter<HttpRequest> adapter = new GoogleClientAdapter(HttpRequest::execute);
    GoogleHttpClientTests googleTests =
        GoogleHttpClientTests.create(adapter, testing.getTestRunner(), testing.getServer());
    return googleTests.all();
  }
}
