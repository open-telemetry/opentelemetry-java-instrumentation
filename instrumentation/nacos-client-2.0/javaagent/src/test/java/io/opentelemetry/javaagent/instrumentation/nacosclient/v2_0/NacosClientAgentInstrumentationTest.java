/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nacosclient.v2_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_NAME;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TRANSPORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.alibaba.nacos.api.config.remote.request.ConfigQueryRequest;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.remote.request.NotifySubscriberRequest;
import com.alibaba.nacos.api.naming.remote.response.NotifySubscriberResponse;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.ServerRequestHandler;
import com.alibaba.nacos.common.remote.client.grpc.GrpcConnection;
import com.alibaba.nacos.shaded.io.grpc.CallOptions;
import com.alibaba.nacos.shaded.io.grpc.ClientCall;
import com.alibaba.nacos.shaded.io.grpc.ManagedChannel;
import com.alibaba.nacos.shaded.io.grpc.MethodDescriptor;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class NacosClientAgentInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void instrumentsGrpcConnectionRequestAdvice() throws Exception {
    GrpcConnection connection =
        new GrpcConnection(new RpcClient.ServerInfo("127.0.0.1", 9848), Runnable::run);
    connection.setChannel(new TestManagedChannel("127.0.0.1:9848"));

    try {
      testing.runWithSpan(
          "parent",
          () ->
              connection.request(
                  ConfigQueryRequest.build("app.yaml", "DEFAULT_GROUP", "tenant-a"), 1000));
    } catch (Throwable expected) {
      // expected
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("Nacos/queryConfig")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfying(
                            equalTo(SERVER_ADDRESS, "127.0.0.1"),
                            equalTo(SERVER_PORT, 9848),
                            equalTo(NETWORK_TRANSPORT, "tcp"),
                            equalTo(NETWORK_PROTOCOL_NAME, "grpc"),
                            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            equalTo(NETWORK_PEER_PORT, 9848),
                            equalTo(stringKey("nacos.category"), "config"),
                            equalTo(stringKey("nacos.request.type"), "ConfigQueryRequest"))));
  }

  @Test
  void instrumentsRpcClientHandleServerRequestAdvice() throws Exception {
    RpcClient rpcClient = mock(RpcClient.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
    ArrayList<ServerRequestHandler> serverRequestHandlers = new ArrayList<>();
    serverRequestHandlers.add(serverRequestHandler());
    setField(rpcClient, "serverRequestHandlers", serverRequestHandlers);
    setRpcClientConfigIfPresent(rpcClient);
    when(rpcClient.getCurrentServer()).thenReturn(new RpcClient.ServerInfo("127.0.0.1", 9848));

    ServiceInfo serviceInfo = new ServiceInfo();
    serviceInfo.setName("com.example.Service");
    serviceInfo.setGroupName("DEFAULT_GROUP");

    Response response =
        testing.runWithSpan(
            "parent", () -> handleServerRequest(rpcClient, notifySubscriberRequest(serviceInfo)));

    assertThat(response).isInstanceOf(NotifySubscriberResponse.class);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("Nacos/notifySubscribeChange")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfying(
                            equalTo(SERVER_ADDRESS, "127.0.0.1"),
                            equalTo(SERVER_PORT, 9848),
                            equalTo(NETWORK_TRANSPORT, "tcp"),
                            equalTo(NETWORK_PROTOCOL_NAME, "grpc"),
                            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            equalTo(NETWORK_PEER_PORT, 9848),
                            equalTo(stringKey("nacos.category"), "naming"),
                            equalTo(stringKey("nacos.request.type"), "NotifySubscriberRequest"),
                            equalTo(stringKey("nacos.group"), "DEFAULT_GROUP"),
                            equalTo(stringKey("nacos.service.name"), "com.example.Service"))));
  }

  private static NotifySubscriberRequest notifySubscriberRequest(ServiceInfo serviceInfo) {
    NotifySubscriberRequest request = new NotifySubscriberRequest();
    request.setServiceInfo(serviceInfo);
    return request;
  }

  private static ServerRequestHandler serverRequestHandler() {
    InvocationHandler handler =
        (proxy, method, args) -> {
          if (method.getDeclaringClass() == Object.class) {
            if ("toString".equals(method.getName())) {
              return "TestServerRequestHandler";
            }
            if ("hashCode".equals(method.getName())) {
              return System.identityHashCode(proxy);
            }
            if ("equals".equals(method.getName())) {
              return proxy == args[0];
            }
          }
          return new NotifySubscriberResponse();
        };
    return (ServerRequestHandler)
        Proxy.newProxyInstance(
            ServerRequestHandler.class.getClassLoader(),
            new Class<?>[] {ServerRequestHandler.class},
            handler);
  }

  private static Response handleServerRequest(RpcClient rpcClient, Request request) {
    try {
      Method method = RpcClient.class.getDeclaredMethod("handleServerRequest", Request.class);
      method.setAccessible(true);
      return (Response) method.invoke(rpcClient, request);
    } catch (ReflectiveOperationException e) {
      throw new LinkageError(e.getMessage(), e);
    }
  }

  private static void setField(Object target, String fieldName, Object value) throws Exception {
    Field field = RpcClient.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static void setRpcClientConfigIfPresent(RpcClient rpcClient) throws Exception {
    Class<?> rpcClientConfigClass;
    try {
      rpcClientConfigClass =
          Class.forName(
              "com.alibaba.nacos.common.remote.client.RpcClientConfig",
              false,
              RpcClient.class.getClassLoader());
    } catch (ClassNotFoundException exception) {
      return;
    }

    Object config =
        Proxy.newProxyInstance(
            rpcClientConfigClass.getClassLoader(),
            new Class<?>[] {rpcClientConfigClass},
            (proxy, method, args) -> {
              if ("name".equals(method.getName())) {
                return "test";
              }
              if ("labels".equals(method.getName())) {
                return Collections.emptyMap();
              }
              if (method.getReturnType() == int.class) {
                return 0;
              }
              if (method.getReturnType() == long.class) {
                return 0L;
              }
              throw new UnsupportedOperationException(method.getName());
            });
    setField(rpcClient, "rpcClientConfig", config);
  }

  private static final class TestManagedChannel extends ManagedChannel {
    private final String authority;

    private TestManagedChannel(String authority) {
      this.authority = authority;
    }

    @Override
    public ManagedChannel shutdown() {
      return this;
    }

    @Override
    public boolean isShutdown() {
      return false;
    }

    @Override
    public boolean isTerminated() {
      return false;
    }

    @Override
    public ManagedChannel shutdownNow() {
      return this;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
      return false;
    }

    @Override
    public <REQ, RESP> ClientCall<REQ, RESP> newCall(
        MethodDescriptor<REQ, RESP> methodDescriptor, CallOptions callOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String authority() {
      return authority;
    }
  }
}
