/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import static io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest.CONNECTION_TIMEOUT;
import static io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest.READ_TIMEOUT;

import com.twitter.finagle.Http;
import com.twitter.finagle.Service;
import com.twitter.finagle.http.Request;
import com.twitter.finagle.http.Response;
import com.twitter.finagle.service.RetryBudget;
import com.twitter.util.Await;
import com.twitter.util.Duration;
import com.twitter.util.Time;
import io.netty.channel.EventLoopGroup;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11.Utils.ClientType;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Class-scoped JUnit extension that owns a Finagle {@link Http.Client} per {@link ClientType} and
 * the underlying {@link EventLoopGroup} for the duration of the test class. Modeled on {@code
 * Netty41ClientExtension}: build once in {@code beforeAll}, tear down once in {@code afterAll}.
 */
public class FinagleClientExtension implements AfterAllCallback {

  private final UnaryOperator<Http.Client> configurer;

  private final Map<ClientType, Http.Client> clients = new ConcurrentHashMap<>();
  private final Map<ServiceKey, Service<Request, Response>> services = new ConcurrentHashMap<>();

  public FinagleClientExtension(UnaryOperator<Http.Client> configurer) {
    this.configurer = configurer;
  }

  @Override
  public void afterAll(ExtensionContext context) throws Exception {
    for (Service<Request, Response> svc : services.values()) {
      Await.ready(svc.close(Time.fromSeconds(10)));
    }
    services.clear();
    clients.clear();
  }

  public Service<Request, Response> getService(URI uri) {
    return getService(uri, "https".equals(uri.getScheme()) ? ClientType.TLS : ClientType.DEFAULT);
  }

  public Service<Request, Response> getService(URI uri, ClientType type) {
    String dest = uri.getHost() + ":" + Utils.safePort(uri);
    ServiceKey key = new ServiceKey(dest, type);
    // Build the client and bind the service under the root OTel context so no test-trace context
    // gets captured in long-lived internal state.
    return services.computeIfAbsent(
        key, k -> Context.root().wrapSupplier(() -> client(type).newService(dest)).get());
  }

  private Http.Client client(ClientType type) {
    return clients.computeIfAbsent(type, this::buildClient);
  }

  private Http.Client buildClient(ClientType type) {
    Http.Client client =
        Http.client()
            .withTransport()
            .readTimeout(Duration.fromMilliseconds(READ_TIMEOUT.toMillis()))
            .withTransport()
            .connectTimeout(Duration.fromMilliseconds(CONNECTION_TIMEOUT.toMillis()))
            .withRetryBudget(RetryBudget.Empty());

    switch (type) {
      case TLS:
        client = client.withTransport().tlsWithoutValidation();
        break;
      case SINGLE_CONN:
        client = client.withSessionPool().maxSize(1);
        break;
      case DEFAULT:
        break;
    }
    return configurer.apply(client);
  }

  private static final class ServiceKey {
    final String dest;
    final ClientType type;

    ServiceKey(String dest, ClientType type) {
      this.dest = dest;
      this.type = type;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ServiceKey)) {
        return false;
      }
      ServiceKey that = (ServiceKey) o;
      return dest.equals(that.dest) && type == that.type;
    }

    @Override
    public int hashCode() {
      return Objects.hash(dest, type);
    }
  }
}
