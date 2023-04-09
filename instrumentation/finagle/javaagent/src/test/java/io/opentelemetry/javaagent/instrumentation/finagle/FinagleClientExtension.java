/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finagle;

import static io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest.CONNECTION_TIMEOUT;
import static io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest.READ_TIMEOUT;

import com.twitter.finagle.Http;
import com.twitter.finagle.Service;
import com.twitter.finagle.http.Method;
import com.twitter.finagle.http.Request;
import com.twitter.finagle.http.Response;
import com.twitter.finagle.netty4.param.WorkerPool;
import com.twitter.finagle.service.RetryBudget;
import com.twitter.util.Await;
import com.twitter.util.Duration;
import com.twitter.util.Time;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class FinagleClientExtension implements BeforeEachCallback, AfterEachCallback {
  private ExecutorService executor;
  private EventLoopGroup eventLoopGroup;
  private Map<URI, Service<Request, Response>> clients;

  private final Function<Http.Client, Http.Client> clientBuilder;

  public FinagleClientExtension(Function<Http.Client, Http.Client> clientBuilder) {
    this.clientBuilder = clientBuilder;
  }

  public static FinagleClientExtension http_1() {
    return new FinagleClientExtension(Http.Client::withNoHttp2);
  }

  // todo implement http/2-specific tests
  //    .withHttp2()
  //    .configured(PriorKnowledge.apply(true).mk())
  public static FinagleClientExtension http_2() {
    return new FinagleClientExtension(Http.Client::withHttp2);
  }

  static int safePort(URI uri) {
    int port = uri.getPort();
    if (port == -1) {
      port = uri.getScheme().equals("https") ? 443 : 80;
    }
    return port;
  }

  static Request buildRequest(String method, URI uri, Map<String, String> headers) {
    Request request =
        Request.apply(
            Method.apply(method.toUpperCase(Locale.ENGLISH)),
            uri.getPath() + (uri.getQuery() == null ? "" : "?" + uri.getRawQuery()));
    request.host(uri.getHost() + ":" + safePort(uri));
    headers.forEach((key, value) -> request.headerMap().put(key, value));
    return request;
  }

  public Http.Client builderFor(URI uri) {
    Http.Client client =
        clientBuilder
            .apply(Http.client())
            .withTransport()
            .readTimeout(Duration.fromMilliseconds(READ_TIMEOUT.toMillis()))
            .withTransport()
            .connectTimeout(Duration.fromMilliseconds(CONNECTION_TIMEOUT.toMillis()))
            // the default WorkerEventLoop.Global uses cached threads which get created and
            // therefore never release their scopes (by default)
            .configured(new WorkerPool(eventLoopGroup).mk())
            // disable automatic retries -- retries will result in under-counting traces in the
            // tests
            .withRetryBudget(RetryBudget.Empty());

    if (uri.getScheme().equals("https")) {
      client = client.withTransport().tlsWithoutValidation();
    }

    return client;
  }

  public Service<Request, Response> clientFor(URI uri) {
    return clients.computeIfAbsent(
        uri, ignored -> builderFor(uri).newService(uri.getHost() + ":" + safePort(uri)));
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    // finagle uses a global event loop group by default which never gets shut down;
    // as such, scopes leak constantly due to how finagle uses a runnable within its own
    // BlockingTimeTrackingThreadFactory;
    //
    // the following elaborate setup wires up an executor and netty event loop group we control for
    // the purposes of testing to achieve the strictest confidence instrumentations work correctly
    executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    if (Epoll.isAvailable()) {
      eventLoopGroup = new EpollEventLoopGroup(2, executor);
    } else {
      eventLoopGroup = new NioEventLoopGroup(2, executor);
    }
    clients = new ConcurrentHashMap<>();
  }

  @Override
  public void afterEach(ExtensionContext context) throws Exception {
    for (Service<Request, Response> client : clients.values()) {
      Await.ready(client.close(Time.fromSeconds(10)));
    }
    eventLoopGroup.shutdownGracefully(2, 2, TimeUnit.SECONDS);
    executor.shutdown();
    if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
      throw new RuntimeException();
    }
  }
}
