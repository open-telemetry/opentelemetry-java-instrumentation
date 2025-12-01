/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.event.EventBus;
import io.lettuce.core.event.EventPublisherOptions;
import io.lettuce.core.metrics.CommandLatencyRecorder;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.resource.Delay;
import io.lettuce.core.resource.DnsResolver;
import io.lettuce.core.resource.EventLoopGroupProvider;
import io.lettuce.core.resource.NettyCustomizer;
import io.lettuce.core.resource.SocketAddressResolver;
import io.lettuce.core.tracing.Tracing;
import io.netty.util.Timer;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractLettuceClientTest {

  protected static class CustomClientResources implements ClientResources {

    private ClientResources delegate;

    protected CustomClientResources(Builder builder) {
      this.delegate = builder.build();
    }

    @Override
    public Builder mutate() {
      return delegate.mutate();
    }

    @Override
    public Future<Boolean> shutdown() {
      return delegate.shutdown();
    }

    @Override
    public Future<Boolean> shutdown(long l, long l1, TimeUnit timeUnit) {
      return delegate.shutdown(l, l1, timeUnit);
    }

    @Override
    public EventPublisherOptions commandLatencyPublisherOptions() {
      return delegate.commandLatencyPublisherOptions();
    }

    @Override
    public CommandLatencyRecorder commandLatencyRecorder() {
      return delegate.commandLatencyRecorder();
    }

    @Override
    public int computationThreadPoolSize() {
      return delegate.computationThreadPoolSize();
    }

    @Override
    public DnsResolver dnsResolver() {
      return delegate.dnsResolver();
    }

    @Override
    public EventBus eventBus() {
      return delegate.eventBus();
    }

    @Override
    public EventLoopGroupProvider eventLoopGroupProvider() {
      return delegate.eventLoopGroupProvider();
    }

    @Override
    public EventExecutorGroup eventExecutorGroup() {
      return delegate.eventExecutorGroup();
    }

    @Override
    public int ioThreadPoolSize() {
      return delegate.ioThreadPoolSize();
    }

    @Override
    public NettyCustomizer nettyCustomizer() {
      return delegate.nettyCustomizer();
    }

    @Override
    public Delay reconnectDelay() {
      return delegate.reconnectDelay();
    }

    @Override
    public SocketAddressResolver socketAddressResolver() {
      return delegate.socketAddressResolver();
    }

    @Override
    public Timer timer() {
      return delegate.timer();
    }

    @Override
    public Tracing tracing() {
      return delegate.tracing();
    }
  }

  protected static final Logger logger = LoggerFactory.getLogger(AbstractLettuceClientTest.class);

  private static final boolean COMMAND_ENCODING_EVENTS_ENABLED =
      Boolean.getBoolean(
          "otel.instrumentation.lettuce.experimental.command-encoding-events.enabled");

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  protected static final int DB_INDEX = 0;

  protected static GenericContainer<?> redisServer =
      new GenericContainer<>("redis:6.2.3-alpine")
          .withExposedPorts(6379)
          .withLogConsumer(new Slf4jLogConsumer(logger))
          .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));

  protected static RedisClient redisClient;
  protected static StatefulRedisConnection<String, String> connection;
  protected static String host;
  protected static String ip;
  protected static int port;
  protected static String embeddedDbUri;

  protected abstract RedisClient createClient(String uri);

  protected abstract InstrumentationExtension testing();

  protected ContainerConnection newContainerConnection() {
    GenericContainer<?> server =
        new GenericContainer<>("redis:6.2.3-alpine")
            .withExposedPorts(6379)
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));
    server.start();
    cleanup.deferCleanup(server::stop);

    long serverPort = server.getMappedPort(6379);

    RedisClient client = createClient("redis://" + host + ":" + serverPort + "/" + DB_INDEX);
    client.setOptions(LettuceTestUtil.CLIENT_OPTIONS);
    cleanup.deferCleanup(client::shutdown);

    StatefulRedisConnection<String, String> statefulConnection = client.connect();
    cleanup.deferCleanup(statefulConnection);

    return new ContainerConnection(statefulConnection, serverPort);
  }

  protected static class ContainerConnection {
    public final StatefulRedisConnection<String, String> connection;
    public final long port;

    private ContainerConnection(StatefulRedisConnection<String, String> connection, long port) {
      this.connection = connection;
      this.port = port;
    }
  }

  protected static List<AttributeAssertion> addExtraAttributes(AttributeAssertion... assertions) {
    return Arrays.asList(assertions);
  }

  protected static void assertCommandEncodeEvents(SpanData span) {
    if (COMMAND_ENCODING_EVENTS_ENABLED) {
      assertThat(span)
          .hasEventsSatisfyingExactly(
              event -> event.hasName("redis.encode.start"),
              event -> event.hasName("redis.encode.end"));
    } else {
      assertThat(span.getEvents())
          .noneSatisfy(event -> assertThat(event).hasName("redis.encode.start"));
      assertThat(span.getEvents())
          .noneSatisfy(event -> assertThat(event).hasName("redis.encode.end"));
    }
  }
}
