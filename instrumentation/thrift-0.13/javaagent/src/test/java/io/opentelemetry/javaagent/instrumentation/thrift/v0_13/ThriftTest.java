/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_13;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import custom.CustomService;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.thrift.v0_13.AbstractThriftTest;
import io.opentelemetry.instrumentation.thrift.v0_13.CustomAsyncHandler;
import io.opentelemetry.instrumentation.thrift.v0_13.CustomHandler;
import java.util.concurrent.CompletableFuture;
import org.apache.thrift.TProcessor;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ThriftTest extends AbstractThriftTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }

  @Override
  protected TProcessor configure(TProcessor processor, String serviceName) {
    return processor;
  }

  @Override
  protected TProtocol configure(TProtocol protocol) {
    return protocol;
  }

  @Override
  protected TProtocolFactory configure(TProtocolFactory protocolFactory) {
    return protocolFactory;
  }

  @Override
  protected CustomService.AsyncIface configure(CustomService.AsyncClient asyncClient) {
    return asyncClient;
  }

  @Override
  protected CustomService.Iface configure(CustomService.Client client) {
    return client;
  }

  @Override
  protected TProtocolFactory configureServerInProtocolFactory(TProtocolFactory protocolFactory) {
    return protocolFactory;
  }

  @Override
  protected TProtocolFactory configureServerOutProtocolFactory(TProtocolFactory protocolFactory) {
    return protocolFactory;
  }

  @Override
  protected boolean hasAsyncServerNetworkAttributes() {
    return true;
  }

  @Override
  protected String getServerRpcServiceName(String handlerClassName) {
    return CustomService.class.getName();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void serviceIdentityDoesNotDependOnSyncHandler(boolean inherited) throws Exception {
    CustomHandler handler = inherited ? new RenamedHandler() : new CustomHandler();
    int port = startSimpleServer(true, null, handler);
    CustomService.Iface client = createClient(port);

    assertThat(client.say("Hello", "World")).isEqualTo("Say Hello World");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertClientSpan(span, "say", port).hasNoParent(),
                span ->
                    assertServerSpan(span, handler.getClass().getName(), "say", port, null)
                        .hasParent(trace.getSpan(0))));
    assertMetrics(port, handler.getClass().getName());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void serviceIdentityDoesNotDependOnAsyncHandler(boolean inherited) throws Exception {
    CustomAsyncHandler handler = inherited ? new RenamedAsyncHandler() : new CustomAsyncHandler();
    int port = startAsyncServer(true, handler);
    CustomService.AsyncIface client = createAsyncClient(port);
    CompletableFuture<String> result = new CompletableFuture<>();

    client.say(
        "Hello",
        "World",
        new AsyncMethodCallback<String>() {
          @Override
          public void onComplete(String response) {
            result.complete(response);
          }

          @Override
          public void onError(Exception exception) {
            result.completeExceptionally(exception);
          }
        });
    assertThat(result.get(15, SECONDS)).isEqualTo("Say Hello World");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertClientSpan(span, "say", port).hasNoParent(),
                span ->
                    assertServerSpan(span, handler.getClass().getName(), "say", port, null)
                        .hasParent(trace.getSpan(0))));
    assertMetrics(port, handler.getClass().getName());
  }

  private static class RenamedHandler extends CustomHandler implements Runnable {
    @Override
    public void run() {}
  }

  private static class RenamedAsyncHandler extends CustomAsyncHandler implements Runnable {
    @Override
    public void run() {}
  }
}
