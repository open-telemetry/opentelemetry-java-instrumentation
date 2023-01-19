package io.opentelemetry.javaagent.instrumentation.googlehttpclient;

import com.google.api.client.http.HttpRequest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptionsBuilder;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTests;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTypeAdapter;
import io.opentelemetry.instrumentation.testing.junit.http.NewHttpClientInstrumentationExtension;
import java.util.Collection;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.RegisterExtension;

import static io.opentelemetry.javaagent.instrumentation.googlehttpclient.NewGoogleHttpClientTests.buildOptions;

class NewGoogleHttpClientSyncTest {

  @RegisterExtension
  static final NewHttpClientInstrumentationExtension testing = NewHttpClientInstrumentationExtension.forAgent();

  @TestFactory
  Collection<DynamicTest> test() {
    HttpClientTypeAdapter<HttpRequest> adapter = new GoogleClientAdapter(HttpRequest::execute);

    HttpClientTestOptions options = buildOptions();
    HttpClientTests<HttpRequest> clientTests = new HttpClientTests<>(testing.getTestRunner(),
        testing.getServer(), options, adapter);

    NewGoogleHttpClientTests googleTests = new NewGoogleHttpClientTests(clientTests, adapter);

    return googleTests.all();
  }
}
