/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_7_0;

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
import org.apache.thrift.transport.TFastFramedTransport;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TNonblockingSocket;
import org.apache.thrift.transport.TNonblockingTransport;
import org.apache.thrift.transport.TSaslClientTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.base.VerifyException;

public class NoReturnTest extends ThriftBaseTest {

  @Test
  public void syncClientSyncSimpleServerNoReturn() throws TException {
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    this.syncClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "noReturn", 1);
  }

  @Test
  public void syncClientSyncSimpleServerNoReturnMuti() throws TException {
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientNoReturn(port);
    }
    this.waitAndAssertTracesClientSyncServerSync(port, "noReturn", 5);
  }

  @Test
  public void syncClientSyncSimpleServerNoReturnParallel() throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    AtomicInteger count = new AtomicInteger(0);
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);
    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncClientNoReturn(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncClientSimpleServerNoReturnParallel field: " + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }
    latch.await();
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync(port, "noReturn", threadCount);
  }

  @Test
  public void syncClientSyncThreadPoolServerNoReturn() throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    this.syncClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "noReturn", 1);
  }

  @Test
  public void syncClientSyncThreadPoolServerNoReturnMuti() throws TException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientNoReturn(port);
    }
    this.waitAndAssertTracesClientSyncServerSync(port, "noReturn", 5);
  }

  @Test
  public void syncClientSyncThreadPoolServerNoReturnParallel()
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
                  this.syncClientNoReturn(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncClientSimpleServerNoReturnParallel field: " + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }
    latch.await();
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync(port, "noReturn", threadCount);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerNoReturn() throws TException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    this.syncFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "noReturn", 1);
  }

  @Test
  public void syncFastFramedClientSyncNonblockingServerNoReturn() throws TException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    this.syncFastFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "noReturn", 1);
  }

  @Test
  public void syncFramedClientSyncNonblockingFastServerNoReturn() throws TException {
    int port = super.getPort();
    this.startSyncNonblockingFastServer(port);
    this.syncFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "noReturn", 1);
  }

  @Test
  public void syncFastFramedClientSyncNonblockingFastServerNoReturn() throws TException {
    int port = super.getPort();
    this.startSyncNonblockingFastServer(port);
    this.syncFastFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "noReturn", 1);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerNoReturnMuti() throws TException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientNoReturn(port);
    }
    this.waitAndAssertTracesClientSyncServerSync(port, "noReturn", 5);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerNoReturnParallel()
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
                  this.syncFramedClientNoReturn(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientSyncNonblockingServerNoReturnParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync(port, "noReturn", threadCount);
  }

  @Test
  public void syncFramedClientSyncHsHaServerNoReturn() throws TException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    this.syncFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "noReturn", 1);
  }

  @Test
  public void syncFastFramedClientSyncHsHaServerNoReturn() throws TException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    this.syncFastFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "noReturn", 1);
  }

  @Test
  public void syncFramedClientSyncHsHaFastServerNoReturn() throws TException {
    int port = super.getPort();
    this.startSyncHsHaFastServer(port);
    this.syncFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "noReturn", 1);
  }

  @Test
  public void syncFastFramedClientSyncHsHaFastServerNoReturn() throws TException {
    int port = super.getPort();
    this.startSyncHsHaFastServer(port);
    this.syncFastFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "noReturn", 1);
  }

  @Test
  public void syncFramedClientSyncHsHaServerNoReturnMuti() throws TException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientNoReturn(port);
    }

    this.waitAndAssertTracesClientSyncServerSync(port, "noReturn", 5);
  }

  @Test
  public void syncFramedClientSyncHsHaServerNoReturnParallel()
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
                  this.syncFramedClientNoReturn(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientSyncHsHaServerNoReturnParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync(port, "noReturn", threadCount);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerNoReturn() throws TException, IOException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    this.nonBlockClientNoReturn(port);
    this.waitAndAssertTracesClientAsyncServerSync(port, "noReturn", 1);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerNoReturnMuti() throws TException, IOException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientNoReturn(port);
    }

    this.waitAndAssertTracesClientAsyncServerSync(port, "noReturn", 5);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerNoReturnParallel()
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
                  this.nonBlockClientNoReturn(port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientSyncNonblockingServerNoReturnParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerSync(port, "noReturn", threadCount);
  }

  @Test
  public void nonBlockClientSyncHsHaServerNoReturn() throws TException, IOException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    this.nonBlockClientNoReturn(port);
    this.waitAndAssertTracesClientAsyncServerSync(port, "noReturn", 1);
  }

  @Test
  public void nonBlockClientSyncHsHaServerNoReturnMuti() throws TException, IOException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientNoReturn(port);
    }

    this.waitAndAssertTracesClientAsyncServerSync(port, "noReturn", 5);
  }

  @Test
  public void nonBlockClientSyncHsHaServerNoReturnParallel()
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
                  this.nonBlockClientNoReturn(port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientSyncHsHaServerNoReturnParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerSync(port, "noReturn", threadCount);
  }

  @Test
  public void syncClientSyncNonblockingServerNoReturnError() {
    int port = super.getPort();
    Exception error = null;
    try {
      this.startSyncNonblockingServer(port);
      this.syncClientNoReturn(port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer(port, "noReturn", 1);
  }

  @Test
  public void syncClientSyncHsHaServerNoReturnError() {
    int port = super.getPort();
    Exception error = null;
    try {
      this.startSyncHsHaServer(port);
      this.syncClientNoReturn(port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer(port, "noReturn", 1);
  }

  public void syncClientNoReturn(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      transport.open();
      TProtocol protocol = new TBinaryProtocol(transport);
      ThriftService.Client client = new ThriftService.Client(protocol);
      this.testing().runWithSpan("parent", () -> client.noReturn(1));
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void nonBlockClientNoReturn(int port) throws TException, IOException {
    TNonblockingTransport transport = new TNonblockingSocket("localhost", port);
    TAsyncClientManager clientManager = new TAsyncClientManager();
    TProtocolFactory protocolFactory = new TBinaryProtocol.Factory();
    ThriftService.AsyncClient.Factory factory =
        new ThriftService.AsyncClient.Factory(clientManager, protocolFactory);
    ThriftService.AsyncClient asyClient = factory.getAsyncClient(transport);
    AsyncMethodCallback<ThriftService.AsyncClient.noReturn_call> callback =
        new AsyncMethodCallback<ThriftService.AsyncClient.noReturn_call>() {
          @Override
          public void onComplete(ThriftService.AsyncClient.noReturn_call s) {}

          @Override
          public void onError(Exception e) {
            throw new VerifyException("nonBlockClientNoReturn test failed", e);
          }
        };
    this.testing().runWithSpan("parent", () -> asyClient.noReturn(1, callback));
  }

  public void syncFramedClientNoReturn(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      TFramedTransport framedTransport = new TFramedTransport(transport);
      framedTransport.open();
      TProtocol protocol = new TBinaryProtocol(framedTransport);
      ThriftService.Client client = new ThriftService.Client(protocol);
      this.testing().runWithSpan("parent", () -> client.noReturn(1));
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void syncFastFramedClientNoReturn(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      TFastFramedTransport framedTransport = new TFastFramedTransport(transport);
      framedTransport.open();
      TProtocol protocol = new TBinaryProtocol(framedTransport);
      ThriftService.Client client = new ThriftService.Client(protocol);
      this.testing().runWithSpan("parent", () -> client.noReturn(1));
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void syncNonblockingSaslClientNoReturn(int port) throws TException, IOException {
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
      this.testing().runWithSpan("parent", () -> client.noReturn(1));
    } catch (Exception e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
  }
}
