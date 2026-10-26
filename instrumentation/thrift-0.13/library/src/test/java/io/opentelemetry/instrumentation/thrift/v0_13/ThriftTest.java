/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.junit.rpc.SemconvRpcStabilityUtil.emitOldRpcSemconv;
import static io.opentelemetry.instrumentation.testing.junit.rpc.SemconvRpcStabilityUtil.emitPreviewRpcSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_LOCAL_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_LOCAL_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import custom.CustomService;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.assertj.core.api.AbstractLongAssert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ThriftTest extends AbstractThriftTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  static ThriftTelemetry telemetry;

  @BeforeAll
  static void setup() {
    telemetry = ThriftTelemetry.create(testing.getOpenTelemetry());
  }

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }

  @Override
  protected TProcessor configure(TProcessor processor, String serviceName) {
    return telemetry.wrapServerProcessor(processor, serviceName);
  }

  @Override
  protected TProtocol configure(TProtocol protocol) {
    return telemetry.wrapClientProtocol(protocol);
  }

  @Override
  protected TProtocolFactory configure(TProtocolFactory protocolFactory) {
    return telemetry.wrapClientProtocolFactory(protocolFactory);
  }

  @Override
  protected CustomService.AsyncIface configure(CustomService.AsyncClient asyncClient) {
    return telemetry.wrapAsyncClient(asyncClient, CustomService.AsyncIface.class);
  }

  @Override
  protected CustomService.Iface configure(CustomService.Client client) {
    return telemetry.wrapClient(client, CustomService.Iface.class);
  }

  @Override
  protected TProtocolFactory configureServerInProtocolFactory(TProtocolFactory protocolFactory) {
    return telemetry.wrapServerInProtocolFactory(protocolFactory);
  }

  @Override
  protected TProtocolFactory configureServerOutProtocolFactory(TProtocolFactory protocolFactory) {
    return telemetry.wrapServerOutProtocolFactory(protocolFactory);
  }

  @Override
  protected boolean hasAsyncServerNetworkAttributes() {
    return false;
  }

  @Test
  @SuppressWarnings("deprecation") // using deprecated semconv
  void serviceNameAloneDoesNotEstablishRecognition() throws Exception {
    TProcessor generated = new CustomService.Processor<>(new CustomHandler());
    @SuppressWarnings(
        "UnnecessaryMethodReference") // Intentionally hide the generated dispatch map.
    TProcessor opaque = generated::process;
    int port = startSimpleServer(telemetry.wrapServerProcessor(opaque, "explicit-service"), null);
    CustomService.Iface client = createClient(port, false);

    assertThat(client.say("Hello", "World")).isEqualTo("Say Hello World");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitPreviewRpcSemconv() ? "apache_thrift" : "explicit-service/say")
                        .hasKind(SpanKind.SERVER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(RPC_METHOD, emitPreviewRpcSemconv() ? "_OTHER" : "say"),
                            equalTo(
                                stringKey("rpc.method_original"),
                                emitPreviewRpcSemconv() ? "explicit-service/say" : null),
                            equalTo(RPC_SERVICE, emitOldRpcSemconv() ? "explicit-service" : null),
                            equalTo(RPC_SYSTEM, emitOldRpcSemconv() ? "apache_thrift" : null),
                            equalTo(
                                RPC_SYSTEM_NAME, emitPreviewRpcSemconv() ? "apache_thrift" : null),
                            equalTo(SERVER_ADDRESS, "127.0.0.1"),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_TYPE, "ipv4"),
                            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(NETWORK_PEER_PORT, AbstractLongAssert::isNotNegative),
                            equalTo(NETWORK_LOCAL_ADDRESS, "127.0.0.1"),
                            equalTo(NETWORK_LOCAL_PORT, port))));
  }

  @ParameterizedTest
  @ValueSource(strings = {"simple", "threadPool"})
  void instrumentedClientPlainSimpleServer(String serverKind) throws Exception {
    int port = startServer(serverKind, false);
    CustomService.Iface client = createClient(port, true);

    assertThat(client.say("Plain", "Server")).isEqualTo("Say Plain Server");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertClientSpan(span, "say", port).hasNoParent()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"simple", "threadPool"})
  void plainClientInstrumentedSimpleServer(String serverKind) throws Exception {
    int port = startServer(serverKind, true);
    CustomService.Iface client = createClient(port, false);

    assertThat(client.say("Instrumented", "Server")).isEqualTo("Say Instrumented Server");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertServerSpan(span, "say", port).hasNoParent()));
  }
}
