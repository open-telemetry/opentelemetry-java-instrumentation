/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedubbo.v2_7;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.junit.rpc.SemconvRpcStabilityUtil.emitPreviewRpcSemconv;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.AbstractDubboTest;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.ServiceLoader;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.exchange.Request;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.protocol.dubbo.DecodeableRpcInvocation;
import org.apache.dubbo.rpc.protocol.dubbo.DubboProtocol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;

class DubboAgentTest extends AbstractDubboTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final String REMOTE_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String REMOTE_SPAN_ID = "00f067aa0ba902b7";
  private static final String TRACEPARENT = "00-" + REMOTE_TRACE_ID + "-" + REMOTE_SPAN_ID + "-01";
  private static final String AMBIENT_TRACE_ID = "11111111111111111111111111111111";
  private static final String AMBIENT_SPAN_ID = "2222222222222222";
  private static final Span AMBIENT_PARENT =
      Span.wrap(
          SpanContext.create(
              AMBIENT_TRACE_ID,
              AMBIENT_SPAN_ID,
              TraceFlags.getSampled(),
              TraceState.getDefault()));

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected boolean canCaptureUnknownServiceSpans() {
    return true;
  }

  @ParameterizedTest
  @CsvSource({"false,false", "true,false", "false,true", "true,true"})
  @SuppressWarnings("deprecation") // Dubbo 2.7 attachment API
  void binaryAttachments(boolean ambientSpan, boolean remoteParent) throws Exception {
    assumeTrue(emitPreviewRpcSemconv());
    RpcInvocation invocation = new RpcInvocation();
    invocation.setMethodName("hello");
    invocation.setParameterTypes(new Class<?>[0]);
    invocation.setAttachment("path", "missing.Service");
    Channel channel = channel();
    DubboProtocol protocol = DubboProtocol.getDubboProtocol();
    Method getInvoker =
        DubboProtocol.class.getDeclaredMethod("getInvoker", Channel.class, Invocation.class);
    getInvoker.setAccessible(true);

    if (remoteParent) {
      invocation.setAttachment("traceparent", TRACEPARENT);
    }
    try (Scope ignored = parentContext(ambientSpan).makeCurrent()) {
      assertThatThrownBy(() -> getInvoker.invoke(protocol, channel, invocation))
          .isInstanceOf(InvocationTargetException.class)
          .hasCauseInstanceOf(RemotingException.class);
    }

    assertParent(ambientSpan, remoteParent);
  }

  @ParameterizedTest
  @CsvSource({"false,false", "true,false", "false,true", "true,true"})
  void tripleHeaders(boolean ambientSpan, boolean remoteParent) throws Exception {
    assumeTrue(emitPreviewRpcSemconv());
    assumeTrue(testLatestDeps(), "Triple requires Dubbo 3.x");
    Class<?> requestClass = Class.forName("org.apache.dubbo.remoting.http12.HttpRequest");
    Object request =
        mock(
            requestClass,
            invocation -> {
              switch (invocation.getMethod().getName()) {
                case "uri":
                  return "/missing.Service/hello";
                case "contentType":
                  return "application/grpc";
                case "header":
                  return remoteParent
                          && "traceparent".contentEquals((CharSequence) invocation.getArgument(0))
                      ? TRACEPARENT
                      : null;
                case "remoteAddr":
                  return "127.0.0.1";
                case "remotePort":
                  return 12345;
                default:
                  return Answers.RETURNS_DEFAULTS.answer(invocation);
              }
            });
    Class<?> modelClass = Class.forName("org.apache.dubbo.rpc.model.FrameworkModel");
    Object model = modelClass.getMethod("defaultModel").invoke(null);
    Class<?> mappingClass =
        Class.forName("org.apache.dubbo.rpc.protocol.tri.h12.grpc.GrpcRequestHandlerMapping");
    Object mapping = mappingClass.getConstructor(modelClass).newInstance(model);
    Method getRequestHandler =
        Arrays.stream(mappingClass.getMethods())
            .filter(method -> method.getName().equals("getRequestHandler"))
            .findFirst()
            .get();

    try (Scope ignored = parentContext(ambientSpan).makeCurrent()) {
      assertThatThrownBy(
              () ->
                  getRequestHandler.invoke(
                      mapping, new URL("tri", "127.0.0.1", 12345), request, null))
          .isInstanceOf(InvocationTargetException.class)
          .cause()
          .hasFieldOrPropertyWithValue("statusCode", 404);
    }

    assertParent(ambientSpan, remoteParent);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void decodingWithoutReadableAttachments(boolean ambientSpan) throws ReflectiveOperationException {
    assumeTrue(emitPreviewRpcSemconv());
    Channel channel = channel();
    InputStream unreadable =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("invocation rejected");
          }

          @Override
          public int read(byte[] bytes, int offset, int length) throws IOException {
            return read();
          }
        };
    DecodeableRpcInvocation invocation = decodeableInvocation(channel, unreadable);

    try (Scope ignored = parentContext(ambientSpan).makeCurrent()) {
      assertThatThrownBy(() -> invocation.decode(channel, unreadable))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("invocation rejected");
    }

    assertParent(ambientSpan, false);
  }

  @Test
  @SuppressWarnings("deprecation") // Dubbo 2.7 RpcContext and attachment APIs
  void suppressesUnknownServiceInsideServerSpan() throws Exception {
    assumeTrue(emitPreviewRpcSemconv());
    DubboProtocol protocol = DubboProtocol.getDubboProtocol();
    Filter serverFilter = null;
    for (Filter filter : ServiceLoader.load(Filter.class)) {
      if (filter.getClass().getSimpleName().equals("OpenTelemetryServerFilter")) {
        serverFilter = filter;
        break;
      }
    }
    assertThat(serverFilter).isNotNull();

    @SuppressWarnings("unchecked")
    Invoker<HelloService> invoker = mock(Invoker.class);
    when(invoker.getInterface()).thenReturn(HelloService.class);
    RpcInvocation outer = new RpcInvocation();
    outer.setInvoker(invoker);
    outer.setMethodName("hello");
    RpcInvocation missing = new RpcInvocation();
    missing.setMethodName("hello");
    missing.setParameterTypes(new Class<?>[0]);
    missing.setAttachment("path", "missing.Service");
    missing.setAttachment("traceparent", TRACEPARENT);
    Channel channel = channel();
    Method getInvoker =
        DubboProtocol.class.getDeclaredMethod("getInvoker", Channel.class, Invocation.class);
    getInvoker.setAccessible(true);
    when(invoker.invoke(outer))
        .thenAnswer(
            ignored -> {
              assertThatThrownBy(() -> getInvoker.invoke(protocol, channel, missing))
                  .isInstanceOf(InvocationTargetException.class)
                  .hasCauseInstanceOf(RemotingException.class);
              return mock(Result.class);
            });

    RpcContext.getContext().setUrl(channel.getUrl());
    try {
      serverFilter.invoke(invoker, outer);
    } finally {
      RpcContext.removeContext();
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(HelloService.class.getName() + "/hello")
                        .hasKind(SpanKind.SERVER)
                        .hasNoParent()));
  }

  private static DecodeableRpcInvocation decodeableInvocation(Channel channel, InputStream input)
      throws ReflectiveOperationException {
    if (testLatestDeps()) {
      Class<?> modelClass = Class.forName("org.apache.dubbo.rpc.model.FrameworkModel");
      Object model = modelClass.getMethod("defaultModel").invoke(null);
      return DecodeableRpcInvocation.class
          .getConstructor(modelClass, Channel.class, Request.class, InputStream.class, byte.class)
          .newInstance(model, channel, new Request(), input, (byte) 2);
    }
    return DecodeableRpcInvocation.class
        .getConstructor(Channel.class, Request.class, InputStream.class, byte.class)
        .newInstance(channel, new Request(), input, (byte) 2);
  }

  private static Channel channel() {
    Channel channel = mock(Channel.class);
    when(channel.getLocalAddress())
        .thenReturn(new InetSocketAddress(InetAddress.getLoopbackAddress(), 12345));
    when(channel.getRemoteAddress())
        .thenReturn(new InetSocketAddress(InetAddress.getLoopbackAddress(), 23456));
    when(channel.getUrl()).thenReturn(new URL("dubbo", "127.0.0.1", 12345));
    return channel;
  }

  private static Context parentContext(boolean ambientSpan) {
    return Context.root()
        .with(ambientSpan ? AMBIENT_PARENT : Span.getInvalid())
        .with(Baggage.builder().put("test-baggage-key-1", "preserved").build());
  }

  private static void assertParent(boolean ambientSpan, boolean remoteParent) {
    assertThat(testing.spans())
        .singleElement()
        .satisfies(
            spanData -> {
              SpanDataAssert span = OpenTelemetryAssertions.assertThat(spanData);
              span.hasName("dubbo").hasKind(SpanKind.SERVER);
              if (remoteParent) {
                span.hasTraceId(REMOTE_TRACE_ID).hasParentSpanId(REMOTE_SPAN_ID);
              } else if (ambientSpan) {
                span.hasTraceId(AMBIENT_TRACE_ID).hasParentSpanId(AMBIENT_SPAN_ID);
              } else {
                span.hasNoParent();
              }
              // BaggageSpanProcessor.onStart reads the non-span baggage ContextKey from parentContext.
              span.hasAttribute(stringKey("test-baggage-key-1"), "preserved");
            });
  }
}
