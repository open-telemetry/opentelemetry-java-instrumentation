package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.client.WebClientBuilder;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.common.util.Exceptions;
import io.opentelemetry.instrumentation.testing.junit.AbstractHttpClientTest;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.BeforeEach;

public abstract class AbstractArmeriaHttpClientTest extends AbstractHttpClientTest<HttpRequest> {

  protected abstract WebClientBuilder configureClient(WebClientBuilder clientBuilder);

  private WebClient client;

  @BeforeEach
  void setupClient() {
    client = configureClient(WebClient.builder()).build();
  }

  @Override
  protected final HttpRequest buildRequest(String method, URI uri, Map<String, String> headers) {
    return HttpRequest.of(
        RequestHeaders.builder(HttpMethod.valueOf(method), uri.toString())
            .set(headers.entrySet())
            .build());
  }

  @Override
  protected final int sendRequest(
      HttpRequest request, String method, URI uri, Map<String, String> headers) {
    try {
      return client.execute(request).aggregate().join().status().code();
    } catch (CompletionException e) {
      return Exceptions.throwUnsafely(e.getCause());
    }
  }

  @Override
  protected final void sendRequestWithCallback(
      HttpRequest request,
      String method,
      URI uri,
      Map<String, String> headers,
      RequestResult requestResult) {
    client
        .execute(request)
        .aggregate()
        .whenComplete(
            (response, throwable) ->
                requestResult.complete(() -> response.status().code(), throwable));
  }
}
