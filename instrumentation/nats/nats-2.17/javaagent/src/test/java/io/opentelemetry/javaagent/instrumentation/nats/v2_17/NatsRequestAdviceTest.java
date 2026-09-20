/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nats.v2_17;

import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.nats.client.Connection;
import io.nats.client.Message;
import io.nats.client.Options;
import io.nats.client.api.ServerInfo;
import io.nats.client.impl.NatsMessage;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.implementation.MethodCall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class NatsRequestAdviceTest {

  private static final AtomicInteger classId = new AtomicInteger();

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void preservesNullResponseFromDelegatedOverload() throws Exception {
    TestConnectionHandler handler = new TestConnectionHandler();
    Connection connection = newConnection(handler);

    assertThat(connection.request("subject", new byte[0], Duration.ofSeconds(1))).isNull();
    assertThat(handler.headerRequestCalls).hasValue(1);
    assertThat(handler.bodyRequestCalls).hasValue(0);
  }

  @Test
  void preservesInterruptionFromDelegatedOverload() throws Exception {
    TestConnectionHandler handler = new TestConnectionHandler();
    InterruptedException interruption = new InterruptedException("test");
    handler.failure = interruption;
    Connection connection = newConnection(handler);

    assertThatThrownBy(() -> connection.request("subject", new byte[0], Duration.ofSeconds(1)))
        .isSameAs(interruption);
    assertThat(handler.headerRequestCalls).hasValue(1);
    assertThat(handler.bodyRequestCalls).hasValue(0);
  }

  @Test
  void preservesNullMessageValidation() throws Exception {
    TestConnectionHandler handler = new TestConnectionHandler();
    Connection connection = newConnection(handler);

    assertThatThrownBy(() -> connection.request((Message) null, Duration.ofSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Message");
    assertThat(handler.messageRequestCalls).hasValue(1);
    assertThat(handler.headerRequestCalls).hasValue(0);
  }

  @Test
  void preservesAsyncBodyFailureFromDelegatedOverload() throws Exception {
    TestConnectionHandler handler = new TestConnectionHandler();
    RuntimeException failure = new IllegalStateException("test");
    handler.failure = failure;
    Connection connection = newConnection(handler);

    assertThatThrownBy(() -> connection.request("subject", new byte[0])).isSameAs(failure);
    assertThat(handler.asyncHeaderRequestCalls).hasValue(1);
    assertThat(handler.asyncBodyRequestCalls).hasValue(0);
  }

  @Test
  void preservesAsyncMessageFailureFromDelegatedOverload() throws Exception {
    TestConnectionHandler handler = new TestConnectionHandler();
    RuntimeException failure = new IllegalStateException("test");
    handler.failure = failure;
    Connection connection = newConnection(handler);
    Message message = new NatsMessage("subject", null, new byte[0]);

    assertThatThrownBy(() -> connection.request(message)).isSameAs(failure);
    assertThat(handler.asyncHeaderRequestCalls).hasValue(1);
    assertThat(handler.asyncMessageRequestCalls).hasValue(0);
  }

  @Test
  void preservesTimeoutAsyncBodyFailureFromDelegatedOverload() throws Exception {
    TestConnectionHandler handler = new TestConnectionHandler();
    RuntimeException failure = new IllegalStateException("test");
    handler.failure = failure;
    Connection connection = newConnection(handler);

    assertThatThrownBy(
            () -> connection.requestWithTimeout("subject", new byte[0], Duration.ofSeconds(1)))
        .isSameAs(failure);
    assertThat(handler.timeoutAsyncHeaderRequestCalls).hasValue(1);
    assertThat(handler.timeoutAsyncBodyRequestCalls).hasValue(0);
  }

  @Test
  void preservesTimeoutAsyncMessageFailureFromDelegatedOverload() throws Exception {
    TestConnectionHandler handler = new TestConnectionHandler();
    RuntimeException failure = new IllegalStateException("test");
    handler.failure = failure;
    Connection connection = newConnection(handler);
    Message message = new NatsMessage("subject", null, new byte[0]);

    assertThatThrownBy(() -> connection.requestWithTimeout(message, Duration.ofSeconds(1)))
        .isSameAs(failure);
    assertThat(handler.timeoutAsyncHeaderRequestCalls).hasValue(1);
    assertThat(handler.timeoutAsyncMessageRequestCalls).hasValue(0);
  }

  @Test
  void preservesAsyncNullMessageValidation() throws Exception {
    TestConnectionHandler handler = new TestConnectionHandler();
    Connection connection = newConnection(handler);

    assertThatThrownBy(() -> connection.request((Message) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Message");
    assertThat(handler.asyncMessageRequestCalls).hasValue(1);
    assertThat(handler.asyncHeaderRequestCalls).hasValue(0);
  }

  @Test
  void preservesTimeoutAsyncNullMessageValidation() throws Exception {
    TestConnectionHandler handler = new TestConnectionHandler();
    Connection connection = newConnection(handler);

    assertThatThrownBy(() -> connection.requestWithTimeout((Message) null, Duration.ofSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Message");
    assertThat(handler.timeoutAsyncMessageRequestCalls).hasValue(1);
    assertThat(handler.timeoutAsyncHeaderRequestCalls).hasValue(0);
  }

  private static Connection newConnection(InvocationHandler handler) throws Exception {
    Class<? extends Connection> connectionClass =
        new ByteBuddy()
            .subclass(Object.class)
            .name("test.nats.TestConnection" + classId.incrementAndGet())
            .implement(Connection.class)
            .defineField("handler", InvocationHandler.class, Modifier.PRIVATE | Modifier.FINAL)
            .defineConstructor(Modifier.PUBLIC)
            .withParameters(InvocationHandler.class)
            .intercept(
                MethodCall.invoke(Object.class.getDeclaredConstructor())
                    .andThen(FieldAccessor.ofField("handler").setsArgumentAt(0)))
            .method(isDeclaredBy(Connection.class))
            .intercept(InvocationHandlerAdapter.toField("handler"))
            .make()
            .load(
                NatsRequestAdviceTest.class.getClassLoader(),
                ClassLoadingStrategy.Default.INJECTION)
            .getLoaded()
            .asSubclass(Connection.class);
    return connectionClass.getConstructor(InvocationHandler.class).newInstance(handler);
  }

  private static final class TestConnectionHandler implements InvocationHandler {
    private final AtomicInteger bodyRequestCalls = new AtomicInteger();
    private final AtomicInteger headerRequestCalls = new AtomicInteger();
    private final AtomicInteger messageRequestCalls = new AtomicInteger();
    private final AtomicInteger asyncBodyRequestCalls = new AtomicInteger();
    private final AtomicInteger asyncHeaderRequestCalls = new AtomicInteger();
    private final AtomicInteger asyncMessageRequestCalls = new AtomicInteger();
    private final AtomicInteger timeoutAsyncBodyRequestCalls = new AtomicInteger();
    private final AtomicInteger timeoutAsyncHeaderRequestCalls = new AtomicInteger();
    private final AtomicInteger timeoutAsyncMessageRequestCalls = new AtomicInteger();
    private final ServerInfo serverInfo = new ServerInfo("{\"client_id\":1}");
    private final Options options = new Options.Builder().build();
    private Throwable failure;

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if (method.getName().equals("getServerInfo")) {
        return serverInfo;
      }
      if (method.getName().equals("getOptions")) {
        return options;
      }
      if (method.getName().equals("request")) {
        if (method.getParameterCount() == 4) {
          headerRequestCalls.incrementAndGet();
          if (failure != null) {
            throw failure;
          }
          return null;
        }
        if (method.getParameterCount() == 3 && method.getParameterTypes()[1] == byte[].class) {
          bodyRequestCalls.incrementAndGet();
          return null;
        }
        if (method.getParameterCount() == 3) {
          asyncHeaderRequestCalls.incrementAndGet();
          if (failure != null) {
            throw failure;
          }
          return null;
        }
        if (method.getParameterCount() == 2) {
          if (method.getParameterTypes()[0] == String.class) {
            asyncBodyRequestCalls.incrementAndGet();
          } else {
            messageRequestCalls.incrementAndGet();
          }
        } else {
          asyncMessageRequestCalls.incrementAndGet();
        }
        if (args[0] == null) {
          throw new IllegalArgumentException("Message");
        }
      }
      if (method.getName().equals("requestWithTimeout")) {
        if (method.getParameterCount() == 4) {
          timeoutAsyncHeaderRequestCalls.incrementAndGet();
          if (failure != null) {
            throw failure;
          }
        } else if (method.getParameterTypes()[0] == String.class) {
          timeoutAsyncBodyRequestCalls.incrementAndGet();
        } else {
          timeoutAsyncMessageRequestCalls.incrementAndGet();
          if (args[0] == null) {
            throw new IllegalArgumentException("Message");
          }
        }
      }
      return null;
    }
  }
}
