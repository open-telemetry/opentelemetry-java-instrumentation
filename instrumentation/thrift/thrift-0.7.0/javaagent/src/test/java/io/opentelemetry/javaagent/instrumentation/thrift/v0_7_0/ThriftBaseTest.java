/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_7_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.thrift.v0_7_0.server.ThriftServiceImpl;
import io.opentelemetry.javaagent.instrumentation.thrift.v0_7_0.thrift.ThriftService;
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Random;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.server.THsHaServer;
import org.apache.thrift.server.TNonblockingServer;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TSimpleServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TFastFramedTransport;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TNonblockingServerSocket;
import org.apache.thrift.transport.TNonblockingServerTransport;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;
import org.apache.thrift.transport.TTransportException;
import org.assertj.core.api.AbstractAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.shaded.com.google.common.base.VerifyException;

public abstract class ThriftBaseTest {
  private static final Logger logger = Logger.getLogger(ThriftBaseTest.class.getName());
  public TServer server;
  public int port = 13100;

  private static final String ASYNC_CLIENT =
      "io.opentelemetry.javaagent.instrumentation.thrift.v0_7_0.thrift.ThriftService$AsyncClient";
  private static final String SYNC_CLIENT =
      "io.opentelemetry.javaagent.instrumentation.thrift.v0_7_0.thrift.ThriftService$Client";
  private static final String SYNC_SERVER =
      "io.opentelemetry.javaagent.instrumentation.thrift.v0_7_0.server.ThriftServiceImpl";
  private static final String PEER_ADDR = "127.0.0.1";

  private static final String TRANSPORT_EXCEPTION =
      "org.apache.thrift.transport.TTransportException";
  private static final String VERIFY_EXCEPTION =
      "org.testcontainers.shaded.com.google.common.base.VerifyException";

  private static final String IO_EXCEPTION = "java.io.IOException";
  private static final String NULL_EXCEPTION = "java.lang.NullPointerException";

  private static final AttributeKey<String> RPC_METHOD = AttributeKey.stringKey("rpc.method");
  private static final AttributeKey<String> RPC_SERVICE = AttributeKey.stringKey("rpc.service");
  private static final AttributeKey<String> RPC_SYSTEM = AttributeKey.stringKey("rpc.system");

  private static final String EXCEPTION_EVENT_NAME = "exception";
  private static final AttributeKey<String> EXCEPTION_MESSAGE =
      AttributeKey.stringKey("exception.message");
  private static final AttributeKey<String> EXCEPTION_TYPE =
      AttributeKey.stringKey("exception.type");

  public static final AttributeKey<String> NETWORK_PEER_ADDRESS =
      AttributeKey.stringKey("network.peer.address");
  public static final AttributeKey<Long> NETWORK_PEER_PORT =
      AttributeKey.longKey("network.peer.port");

  @RegisterExtension
  protected static InstrumentationExtension testing = AgentInstrumentationExtension.create();

  protected InstrumentationExtension testing() {
    return testing;
  }

  @BeforeEach
  public void before() throws InterruptedException, IOException {
    ++this.port;
    logger.info(
        "before port="
            + this.port
            + ", threadName="
            + Thread.currentThread().getName()
            + ", threadId="
            + Thread.currentThread().getId());
    this.testing().clearData();
  }

  @AfterEach
  public void after() throws InterruptedException, IOException {
    this.stopServer();
  }

  public int getPort() {
    Random random = new Random();
    int newPort = this.port + random.nextInt(2000);
    while (portNotRelease(newPort)) {
      newPort = this.port + random.nextInt(2000);
    }
    return newPort;
  }

  public void startSyncSimpleServer(int port) throws TTransportException {
    ThriftServiceImpl impl = new ThriftServiceImpl();
    ThriftService.Processor<ThriftServiceImpl> processor =
        new ThriftService.Processor<ThriftServiceImpl>(impl);
    TServerTransport serverTransport = new TServerSocket(port);
    this.server = new TSimpleServer(new TServer.Args(serverTransport).processor(processor));
    new Thread(
            () -> {
              logger.info(
                  "Starting startSyncSimpleServer threadName="
                      + Thread.currentThread().getName()
                      + ", threadId="
                      + Thread.currentThread().getId());
              this.server.serve();
            })
        .start();
  }

  public void startSyncNonblockingServer(int port) throws TTransportException {
    ThriftServiceImpl impl = new ThriftServiceImpl();
    ThriftService.Processor<ThriftService.Iface> processor =
        new ThriftService.Processor<ThriftService.Iface>(impl);
    TNonblockingServerSocket transport = new TNonblockingServerSocket(port);
    TNonblockingServer.Args serverArgs =
        new TNonblockingServer.Args(transport).processor(processor);
    this.server = new TNonblockingServer(serverArgs);
    new Thread(
            () -> {
              logger.info(
                  "Starting startNonBlockingServer threadName="
                      + Thread.currentThread().getName()
                      + ", threadId="
                      + Thread.currentThread().getId());
              this.server.serve();
            })
        .start();
  }

  public void startSyncNonblockingFastServer(int port) throws TTransportException {
    ThriftServiceImpl impl = new ThriftServiceImpl();
    ThriftService.Processor<ThriftService.Iface> processor =
        new ThriftService.Processor<ThriftService.Iface>(impl);
    TNonblockingServerTransport transport = new TNonblockingServerSocket(port);
    TNonblockingServer.Args serverArgs =
        new TNonblockingServer.Args(transport)
            .processor(processor)
            .transportFactory(new TFastFramedTransport.Factory())
            .protocolFactory(new TBinaryProtocol.Factory());
    this.server = new TNonblockingServer(serverArgs);
    new Thread(
            () -> {
              logger.info(
                  "Starting startNonBlockingServer threadName="
                      + Thread.currentThread().getName()
                      + ", threadId="
                      + Thread.currentThread().getId());
              this.server.serve();
            })
        .start();
  }

  public void startSyncThreadPoolServer(int port) throws TTransportException {
    ThriftServiceImpl impl = new ThriftServiceImpl();
    ThriftService.Processor<ThriftService.Iface> processor =
        new ThriftService.Processor<ThriftService.Iface>(impl);
    TServerSocket transport = new TServerSocket(port);
    TThreadPoolServer.Args serverArgs = new TThreadPoolServer.Args(transport).processor(processor);
    TServer server = new TThreadPoolServer(serverArgs);
    new Thread(
            () -> {
              logger.info(
                  "Starting startSyncThreadPoolServer threadName="
                      + Thread.currentThread().getName()
                      + ", threadId="
                      + Thread.currentThread().getId());
              server.serve();
            })
        .start();
  }

  public void startSyncHsHaServer(int port) throws TTransportException {
    ThriftServiceImpl impl = new ThriftServiceImpl();
    ThriftService.Processor<ThriftService.Iface> processor =
        new ThriftService.Processor<ThriftService.Iface>(impl);
    TNonblockingServerTransport transport = new TNonblockingServerSocket(port);
    TBinaryProtocol.Factory protocolFactory = new TBinaryProtocol.Factory();
    TFramedTransport.Factory transportFactory = new TFramedTransport.Factory();
    THsHaServer.Args serverArgs =
        new THsHaServer.Args(transport)
            .processor(processor)
            .protocolFactory(protocolFactory)
            .transportFactory(transportFactory);
    this.server = new THsHaServer(serverArgs);
    new Thread(
            () -> {
              logger.info(
                  "Starting startSyncTHsHaServer threadName="
                      + Thread.currentThread().getName()
                      + ", threadId="
                      + Thread.currentThread().getId());
              this.server.serve();
            })
        .start();
  }

  public void startSyncHsHaFastServer(int port) throws TTransportException {
    ThriftServiceImpl impl = new ThriftServiceImpl();
    ThriftService.Processor<ThriftService.Iface> processor =
        new ThriftService.Processor<ThriftService.Iface>(impl);
    TNonblockingServerTransport transport = new TNonblockingServerSocket(port);
    TBinaryProtocol.Factory protocolFactory = new TBinaryProtocol.Factory();
    TFastFramedTransport.Factory transportFactory = new TFastFramedTransport.Factory();
    THsHaServer.Args serverArgs =
        new THsHaServer.Args(transport)
            .processor(processor)
            .protocolFactory(protocolFactory)
            .transportFactory(transportFactory);
    this.server = new THsHaServer(serverArgs);
    new Thread(
            () -> {
              logger.info(
                  "Starting startSyncTHsHaServer threadName="
                      + Thread.currentThread().getName()
                      + ", threadId="
                      + Thread.currentThread().getId());
              this.server.serve();
            })
        .start();
  }

  @SuppressWarnings("Interruption")
  public void stopServer() {
    if (this.server != null) {
      this.server.stop();
    }
  }

  public static boolean portNotRelease(int port) {
    // 查找占用端口的进程 ID
    Process process = null;
    String pid = null; // 获取 PID
    try {
      process = Runtime.getRuntime().exec("lsof -ti:" + port);
      BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(process.getInputStream(), Charset.defaultCharset()));
      pid = reader.readLine();
    } catch (IOException e) {
      throw new VerifyException(e);
    }
    return pid != null && !pid.isEmpty();
  }

  public void waitAndAssertTracesClientSyncServerSync(int peerPort, String method, int count) {
    this.baseWaitAndAssertTraces(
        method,
        method,
        count,
        SYNC_CLIENT,
        StatusData.unset(),
        peerPort,
        PEER_ADDR,
        null,
        null,
        SYNC_SERVER,
        StatusData.unset(),
        null,
        null);
  }

  public void waitAndAssertTracesClientSyncServerSyncOneWay(
      int peerPort, String method, int count) {
    this.baseWaitAndAssertTraces(
        method,
        method,
        count,
        SYNC_CLIENT,
        StatusData.unset(),
        peerPort,
        PEER_ADDR,
        null,
        null,
        SYNC_SERVER,
        StatusData.error(),
        val ->
            val.isIn(
                "Cannot invoke \"org.apache.thrift.TBase.write(org.apache.thrift.protocol.TProtocol)\" because \"result\" is null",
                "java.net.SocketException: Broken pipe"),
        NULL_EXCEPTION);
  }

  @SuppressWarnings("UngroupedOverloads")
  public void waitAndAssertTracesClientSyncServerSyncWithError(
      int peerPort, String method, int count) {
    this.baseWaitAndAssertTraces(
        method,
        method,
        count,
        SYNC_CLIENT,
        StatusData.error(),
        peerPort,
        PEER_ADDR,
        val -> val.isIn(null, "Internal error processing " + method),
        TRANSPORT_EXCEPTION,
        SYNC_SERVER,
        StatusData.error(),
        val -> val.isIn("fail"),
        VERIFY_EXCEPTION);
  }

  public void waitAndAssertTracesClientSyncServerSyncOnewayError(
      int peerPort, String method, int count) {
    this.baseWaitAndAssertTraces(
        method,
        method,
        count,
        SYNC_CLIENT,
        StatusData.unset(),
        peerPort,
        PEER_ADDR,
        null,
        null,
        SYNC_SERVER,
        StatusData.error(),
        val -> val.isIn("fail"),
        VERIFY_EXCEPTION);
  }

  public void waitAndAssertTracesClientAsyncServerSync(int peerPort, String method, int count) {
    this.baseWaitAndAssertTraces(
        method,
        method,
        count,
        ASYNC_CLIENT,
        StatusData.unset(),
        peerPort,
        PEER_ADDR,
        null,
        null,
        SYNC_SERVER,
        StatusData.unset(),
        null,
        null);
  }

  public void waitAndAssertTracesClientAsyncServerSyncOneWay(
      int peerPort, String method, int count) {
    this.baseWaitAndAssertTraces(
        method,
        method,
        count,
        ASYNC_CLIENT,
        StatusData.unset(),
        peerPort,
        PEER_ADDR,
        null,
        null,
        SYNC_SERVER,
        StatusData.error(),
        val ->
            val.isIn(
                "Cannot invoke \"org.apache.thrift.TBase.write(org.apache.thrift.protocol.TProtocol)\" because \"result\" is null"),
        NULL_EXCEPTION);
  }

  public void waitAndAssertTracesClientAsyncServerSyncWithError(
      int peerPort, String method, int count) {
    this.baseWaitAndAssertTraces(
        method,
        method,
        count,
        ASYNC_CLIENT,
        StatusData.error(),
        peerPort,
        PEER_ADDR,
        val -> val.isIn("Read call frame size failed", "Internal error processing " + method),
        IO_EXCEPTION,
        SYNC_SERVER,
        StatusData.error(),
        val -> val.isIn("fail"),
        VERIFY_EXCEPTION);
  }

  public void waitAndAssertTracesClientAsyncServerSyncOnewayError(
      int peerPort, String method, int count) {
    this.baseWaitAndAssertTraces(
        method,
        method,
        count,
        ASYNC_CLIENT,
        StatusData.unset(),
        peerPort,
        PEER_ADDR,
        null,
        null,
        SYNC_SERVER,
        StatusData.error(),
        val -> val.isIn("fail"),
        VERIFY_EXCEPTION);
  }

  public void waitAndAssertTracesClientSyncNoServer(int peerPort, String method, int count) {
    this.baseWaitAndAssertTraces(
        method,
        method,
        count,
        SYNC_CLIENT,
        StatusData.error(),
        peerPort,
        PEER_ADDR,
        val ->
            val.isIn(
                null, "Socket is closed by peer.", "java.net.SocketException: Connection reset"),
        TRANSPORT_EXCEPTION,
        null,
        null,
        null,
        null);
  }

  public void waitAndAssertTracesClientSyncNoServerOneway(int peerPort, String method, int count) {
    this.baseWaitAndAssertTraces(
        method,
        method,
        count,
        SYNC_CLIENT,
        StatusData.unset(),
        peerPort,
        PEER_ADDR,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  @SuppressWarnings({"rawtypes", "unchecked"}) // 测试代码
  private void baseWaitAndAssertTraces(
      String clientMethod,
      String serverMethod,
      int count,
      String clientClass,
      StatusData clientStatus,
      int peerPort,
      String peerAddr,
      OpenTelemetryAssertions.StringAssertConsumer clientAssertion,
      String clientErrorType,
      String serverClass,
      StatusData serverStatus,
      OpenTelemetryAssertions.StringAssertConsumer serviceAssertion,
      String serverErrorType) {
    Consumer<TraceAssert>[] consumers = new Consumer[count];
    Consumer<TraceAssert> traceAssertConsumer;
    if (serverClass == null) {
      traceAssertConsumer =
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                  clientSpanDataAssertConsumer(
                      clientMethod,
                      clientClass,
                      clientStatus,
                      trace,
                      peerPort,
                      peerAddr,
                      clientAssertion,
                      clientErrorType));
    } else {
      traceAssertConsumer =
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                  clientSpanDataAssertConsumer(
                      clientMethod,
                      clientClass,
                      clientStatus,
                      trace,
                      peerPort,
                      peerAddr,
                      clientAssertion,
                      clientErrorType),
                  serverSpanDataAssertConsumer(
                      serverMethod,
                      serverClass,
                      serverStatus,
                      trace,
                      serviceAssertion,
                      serverErrorType));
    }

    for (int i = 0; i < count; ++i) {
      consumers[i] = traceAssertConsumer;
    }
    this.testing().waitAndAssertTraces(consumers);
  }

  @SuppressWarnings({"ReturnValueIgnored"})
  private static Consumer<SpanDataAssert> clientSpanDataAssertConsumer(
      String clientMethod,
      String clientClass,
      StatusData statusData,
      TraceAssert trace,
      int peerPort,
      String peerAddr,
      OpenTelemetryAssertions.StringAssertConsumer errorMsgAssertion,
      String errorType) {
    Consumer<SpanDataAssert> consumer =
        span ->
            span.hasName(clientMethod)
                .hasKind(SpanKind.CLIENT)
                .hasParent(trace.getSpan(0))
                .hasStatus(statusData)
                .hasAttributesSatisfying(
                    equalTo(NETWORK_PEER_PORT, peerPort),
                    equalTo(NETWORK_PEER_ADDRESS, peerAddr),
                    equalTo(RPC_SYSTEM, "thrift"),
                    equalTo(RPC_SERVICE, clientClass),
                    equalTo(RPC_METHOD, clientMethod));
    if (statusData == StatusData.error()) {
      consumer =
          consumer.andThen(
              span ->
                  span.hasEventsSatisfyingExactly(
                      event ->
                          event
                              .hasName(EXCEPTION_EVENT_NAME)
                              .hasAttributesSatisfyingExactly(
                                  satisfies(EXCEPTION_MESSAGE, errorMsgAssertion),
                                  satisfies(
                                      AttributeKey.stringKey("exception.stacktrace"),
                                      AbstractAssert::isNotNull),
                                  equalTo(EXCEPTION_TYPE, errorType))));
    }
    return consumer;
  }

  @SuppressWarnings({"ReturnValueIgnored"})
  private static Consumer<SpanDataAssert> serverSpanDataAssertConsumer(
      String serverMethod,
      String serverClass,
      StatusData statusData,
      TraceAssert trace,
      OpenTelemetryAssertions.StringAssertConsumer errorMsgAssertion,
      String errorType) {
    Consumer<SpanDataAssert> consumer =
        span ->
            span.hasName(serverMethod)
                .hasKind(SpanKind.SERVER)
                .hasParent(trace.getSpan(1))
                .hasStatus(statusData)
                .hasAttributesSatisfying(
                    equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
                    equalTo(RPC_SYSTEM, "thrift"),
                    equalTo(RPC_SERVICE, serverClass),
                    equalTo(RPC_METHOD, serverMethod));
    if (statusData == StatusData.error()) {
      consumer =
          consumer.andThen(
              span ->
                  span.hasEventsSatisfyingExactly(
                      event ->
                          event
                              .hasName(EXCEPTION_EVENT_NAME)
                              .hasAttributesSatisfyingExactly(
                                  satisfies(EXCEPTION_MESSAGE, errorMsgAssertion),
                                  satisfies(
                                      AttributeKey.stringKey("exception.stacktrace"),
                                      AbstractAssert::isNotNull),
                                  equalTo(EXCEPTION_TYPE, errorType))));
    }
    return consumer;
  }
}
