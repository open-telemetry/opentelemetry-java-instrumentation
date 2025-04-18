/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_7_0;

import com.google.common.base.VerifyException;
import io.opentelemetry.javaagent.instrumentation.thrift.v0_7_0.thrift.ThriftService;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.security.sasl.Sasl;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.async.TAsyncClientManager;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TNonblockingSocket;
import org.apache.thrift.transport.TNonblockingTransport;
import org.apache.thrift.transport.TSaslClientTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class OneWayTest extends ThriftBaseTest {

  @Test
  public void syncClientSyncSimpleServerOneWay() throws TException {
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    this.syncClientOneWay(port);
    this.waitAndAssertTracesClientSyncServerSyncOneWay("oneWay", 1);
  }

  @Test
  public void syncClientSyncSimpleServerOneWayMuti() throws TException {
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientOneWay(port);
    }
    this.waitAndAssertTracesClientSyncServerSyncOneWay("oneWay", 5);
  }

  @Test
  public void syncClientSyncSimpleServerOneWayParallel() throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    AtomicInteger count = new AtomicInteger(0);
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);
    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncClientOneWay(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncClientSimpleServerOneWayParallel field: " + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }
    latch.await();
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSyncOneWay("oneWay", threadCount);
  }

  @Test
  public void syncClientSyncThreadPoolServerOneWay() throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    this.syncClientOneWay(port);
    this.waitAndAssertTracesClientSyncServerSyncOneWay("oneWay", 1);
  }

  @Test
  public void syncClientSyncThreadPoolServerOneWayMuti() throws TException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientOneWay(port);
    }
    this.waitAndAssertTracesClientSyncServerSyncOneWay("oneWay", 5);
  }

  @Test
  public void syncClientSyncThreadPoolServerOneWayParallel()
      throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    AtomicInteger count = new AtomicInteger(0);
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);
    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncClientOneWay(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncClientSimpleServerOneWayParallel field: " + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }
    latch.await();
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSyncOneWay("oneWay", threadCount);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerOneWay() throws TException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    this.syncFramedClientOneWay(port);
    this.waitAndAssertTracesClientSyncServerSyncOneWay("oneWay", 1);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerOneWayMuti() throws TException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientOneWay(port);
    }

    this.waitAndAssertTracesClientSyncServerSyncOneWay("oneWay", 5);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerOneWayParallel()
      throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientOneWay(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientSyncNonblockingServerOneWayParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSyncOneWay("oneWay", threadCount);
  }

  @Test
  public void syncFramedClientSyncHsHaServerOneWay() throws TException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    this.syncFramedClientOneWay(port);
    this.waitAndAssertTracesClientSyncServerSyncOneWay("oneWay", 1);
  }

  @Test
  public void syncFramedClientSyncHsHaServerOneWayMuti() throws TException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientOneWay(port);
    }

    this.waitAndAssertTracesClientSyncServerSyncOneWay("oneWay", 5);
  }

  @Test
  public void syncFramedClientSyncHsHaServerOneWayParallel()
      throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientOneWay(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientSyncHsHaServerOneWayParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSyncOneWay("oneWay", threadCount);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerOneWay() throws TException, IOException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    this.nonBlockClientOneWay(port);
    this.waitAndAssertTracesClientAsyncServerSyncOneWay("oneWay", 1);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerOneWayMuti() throws TException, IOException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientOneWay(port);
    }

    this.waitAndAssertTracesClientAsyncServerSyncOneWay("oneWay", 5);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerOneWayParallel()
      throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientOneWay(port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientSyncNonblockingServerOneWayParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerSyncOneWay("oneWay", threadCount);
  }

  @Test
  public void nonBlockClientSyncHsHaServerOneWay() throws TException, IOException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    this.nonBlockClientOneWay(port);
    this.waitAndAssertTracesClientAsyncServerSyncOneWay("oneWay", 1);
  }

  @Test
  public void nonBlockClientSyncHsHaServerOneWayMuti() throws TException, IOException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientOneWay(port);
    }

    this.waitAndAssertTracesClientAsyncServerSyncOneWay("oneWay", 5);
  }

  @Test
  public void nonBlockClientSyncHsHaServerOneWayParallel() throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientOneWay(port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientSyncHsHaServerOneWayParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerSyncOneWay("oneWay", threadCount);
  }

  @Test
  public void syncClientSyncNonblockingServerOneWayError() {
    Exception error = null;
    try {
      int port = super.getPort();
      this.startSyncNonblockingServer(port);
      this.syncClientOneWay(port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNull();
    this.waitAndAssertTracesClientSyncNoServerOneway("oneWay", 1);
  }

  @Test
  public void syncClientSyncHsHaServerOneWayError() {
    Exception error = null;
    try {
      int port = super.getPort();
      this.startSyncHsHaServer(port);
      this.syncClientOneWay(port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNull();
    this.waitAndAssertTracesClientSyncNoServerOneway("oneWay", 1);
  }

  public void syncClientOneWay(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      transport.open();
      TProtocol protocol = new TBinaryProtocol(transport);
      ThriftService.Client client = new ThriftService.Client(protocol);
      this.testing().runWithSpan("parent", () -> client.oneWay());
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void nonBlockClientOneWay(int port) throws TException, IOException {
    TNonblockingTransport transport = new TNonblockingSocket("localhost", port);
    TAsyncClientManager clientManager = new TAsyncClientManager();
    TProtocolFactory protocolFactory = new TBinaryProtocol.Factory();
    ThriftService.AsyncClient.Factory factory =
        new ThriftService.AsyncClient.Factory(clientManager, protocolFactory);
    ThriftService.AsyncClient asyClient = factory.getAsyncClient(transport);
    AsyncMethodCallback<ThriftService.AsyncClient.oneWay_call> callback =
        new AsyncMethodCallback<ThriftService.AsyncClient.oneWay_call>() {
          @Override
          public void onComplete(ThriftService.AsyncClient.oneWay_call no) {}

          @Override
          public void onError(Exception e) {
            throw new VerifyException("nonBlockClientOneWay test failed", e);
          }
        };
    this.testing().runWithSpan("parent", () -> asyClient.oneWay(callback));
  }

  public void syncFramedClientOneWay(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      TFramedTransport framedTransport = new TFramedTransport(transport);
      framedTransport.open();
      TProtocol protocol = new TBinaryProtocol(framedTransport);
      ThriftService.Client client = new ThriftService.Client(protocol);
      this.testing().runWithSpan("parent", () -> client.oneWay());
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void syncNonblockingSaslClientOneWay(int port) throws IOException {
    Map<String, String> saslOptions = new HashMap<String, String>();
    saslOptions.put(Sasl.QOP, "auth");
    saslOptions.put(Sasl.SERVER_AUTH, "true");
    TNonblockingTransport nonblockingTransport = new TNonblockingSocket("localhost", port);
    TTransport transport =
        new TSaslClientTransport(
            "PLAIN", // SASL 机制
            null, // Authorization ID
            null, // Authentication ID (用户名)
            null, // Password
            saslOptions, // SASL properties
            new TestSaslCallbackHandler("12345"), // Callback handler
            nonblockingTransport // 底层传输
            );
    TProtocol protocol = new TBinaryProtocol(transport);
    ThriftService.Client client = new ThriftService.Client(protocol);
    Exception error = null;
    try {
      this.testing().runWithSpan("parent", () -> client.oneWay());
    } catch (Exception e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
  }
}
