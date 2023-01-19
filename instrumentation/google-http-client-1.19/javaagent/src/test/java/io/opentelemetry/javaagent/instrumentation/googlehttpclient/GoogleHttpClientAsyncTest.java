package io.opentelemetry.javaagent.instrumentation.googlehttpclient;

import com.google.api.client.http.HttpRequest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTypeAdapter;
import io.opentelemetry.instrumentation.testing.junit.http.NewHttpClientInstrumentationExtension;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.RegisterExtension;
import java.util.Collection;

class GoogleHttpClientAsyncTest {

  @RegisterExtension
  static final NewHttpClientInstrumentationExtension testing = NewHttpClientInstrumentationExtension.forAgent();

  @TestFactory
  Collection<DynamicTest> test() {
    HttpClientTypeAdapter<HttpRequest> adapter = new GoogleClientAdapter(
        request -> request.executeAsync().get());
    GoogleHttpClientTests googleTests = GoogleHttpClientTests.create(adapter, testing.getTestRunner(), testing.getServer());
    return googleTests.all();
  }
}
