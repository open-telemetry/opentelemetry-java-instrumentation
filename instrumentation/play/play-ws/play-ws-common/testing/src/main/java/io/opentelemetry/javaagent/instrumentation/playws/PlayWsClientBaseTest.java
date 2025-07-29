/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws;

import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.ActorMaterializerSettings;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import play.shaded.ahc.io.netty.resolver.InetNameResolver;
import play.shaded.ahc.io.netty.util.concurrent.EventExecutor;
import play.shaded.ahc.io.netty.util.concurrent.ImmediateEventExecutor;
import play.shaded.ahc.io.netty.util.concurrent.Promise;
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClient;
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClientConfig;
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClient;
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClientConfig;
import play.shaded.ahc.org.asynchttpclient.RequestBuilderBase;

abstract class PlayWsClientBaseTest<REQUEST> extends AbstractHttpClientTest<REQUEST> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  private static ActorSystem system;
  protected static AsyncHttpClient asyncHttpClient;
  protected static AsyncHttpClient asyncHttpClientWithReadTimeout;
  protected static ActorMaterializer materializer;

  @BeforeAll
  static void setupHttpClient() {
    String name = "play-ws";
    system = ActorSystem.create(name);
    materializer = ActorMaterializer.create(ActorMaterializerSettings.create(system), system, name);

    // Replace dns name resolver with custom implementation that returns only once address for each
    // host. This is needed for "connection error dropped request" because in case of connection
    // failure ahc will try the next address which isn't necessary for this test.
    RequestBuilderBase.DEFAULT_NAME_RESOLVER =
        new CustomNameResolver(ImmediateEventExecutor.INSTANCE);

    asyncHttpClient = createClient(false);
    asyncHttpClientWithReadTimeout = createClient(true);
  }

  @AfterAll
  static void cleanupHttpClient() throws IOException {
    asyncHttpClient.close();
    asyncHttpClientWithReadTimeout.close();
    system.terminate();
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    super.configure(optionsBuilder);
    // apparently play ws does not report the 302 status code
    optionsBuilder.setResponseCodeOnRedirectError(null);
    optionsBuilder.setMaxRedirects(3);
    optionsBuilder.spanEndsAfterBody();
    optionsBuilder.setHttpAttributes(
        uri -> {
          Set<AttributeKey<?>> attributes =
              new HashSet<>(HttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES);
          attributes.remove(NETWORK_PROTOCOL_VERSION);
          if (uri.toString().endsWith("/circular-redirect")) {
            attributes.remove(SERVER_ADDRESS);
            attributes.remove(SERVER_PORT);
          }
          return attributes;
        });
  }

  private static AsyncHttpClient createClient(boolean readTimeout) {
    DefaultAsyncHttpClientConfig.Builder builder =
        new DefaultAsyncHttpClientConfig.Builder()
            .setMaxRequestRetry(0)
            .setShutdownQuietPeriod(0)
            .setShutdownTimeout(0)
            .setMaxRedirects(3)
            .setConnectTimeout(5000);

    if (readTimeout) {
      builder.setReadTimeout(2000);
    }

    AsyncHttpClientConfig asyncHttpClientConfig = builder.build();
    return new DefaultAsyncHttpClient(asyncHttpClientConfig);
  }

  private static class CustomNameResolver extends InetNameResolver {

    public CustomNameResolver(EventExecutor executor) {
      super(executor);
    }

    @Override
    protected void doResolve(String inetHost, Promise<InetAddress> promise) throws Exception {
      try {
        promise.setSuccess(InetAddress.getByName(inetHost));
      } catch (UnknownHostException exception) {
        promise.setFailure(exception);
      }
    }

    @Override
    protected void doResolveAll(String inetHost, Promise<List<InetAddress>> promise)
        throws Exception {
      try {
        // default implementation calls InetAddress.getAllByName
        promise.setSuccess(Collections.singletonList(InetAddress.getByName(inetHost)));
      } catch (UnknownHostException exception) {
        promise.setFailure(exception);
      }
    }
  }
}
