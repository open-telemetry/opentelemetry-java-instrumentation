package io.opentelemetry.javaagent.instrumentation.googlehttpclient;

import com.google.api.client.http.HttpRequest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptionsBuilder;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTests;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTypeAdapter;
import io.opentelemetry.instrumentation.testing.junit.http.NewHttpClientInstrumentationExtension;
import java.util.Arrays;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.RegisterExtension;

class NewGoogleHttpClientSyncTest {

  @RegisterExtension
  static final NewHttpClientInstrumentationExtension testing = NewHttpClientInstrumentationExtension.forAgent();

  @TestFactory
  Collection<DynamicTest> test() throws Exception {
    HttpClientTypeAdapter<HttpRequest> adapter = new GoogleClientAdapter(HttpRequest::execute);
    HttpClientTestOptions options = buildOptions();

    HttpClientTests<HttpRequest> delegate = new HttpClientTests<>(testing.getTestRunner(),
        testing.getServer(), options, adapter);

    //TODO: Will eventually delegate to all() method
    return Arrays.asList(
        delegate.successfulRequestWithNotSampledParent()
    );
  }

  @NotNull
  private static HttpClientTestOptions buildOptions() {
    HttpClientTestOptionsBuilder builder = HttpClientTestOptions.builder();

    // executeAsync does not actually allow asynchronous execution since it returns a standard
    // Future which cannot have callbacks attached. We instrument execute and executeAsync
    // differently so test both but do not need to run our normal asynchronous tests, which check
    // context propagation, as there is no possible context propagation.
    builder.disableTestCallback();
    builder.enableTestReadTimeout();

    // Circular redirects don't throw an exception with Google Http Client
    builder.disableTestCircularRedirects();
    return builder.build();
  }


}
