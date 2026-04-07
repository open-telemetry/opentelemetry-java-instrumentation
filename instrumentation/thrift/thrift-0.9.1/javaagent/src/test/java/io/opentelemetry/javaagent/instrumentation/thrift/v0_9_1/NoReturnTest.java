/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.thrift.ThriftService;
import java.io.IOException;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.async.TAsyncClientManager;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TMultiplexedProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TNonblockingSocket;
import org.apache.thrift.transport.TNonblockingTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.junit.jupiter.api.Test;

public class NoReturnTest extends ThriftBaseTest {

  @Test
  public void syncClientSyncSimpleServerNoReturn() throws TException {
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    this.syncClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "noReturn", 1);
  }

  @Test
  public void syncClientSyncSimpleServerNoReturnParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncClientNoReturn(port));
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
  public void syncClientSyncThreadPoolServerNoReturnParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncClientNoReturn(port));
    this.waitAndAssertTracesClientSyncServerSync(port, "noReturn", threadCount);
  }

  @Test
  public void syncClientMutiSyncSimpleServerNoReturn() throws TException {
    int port = super.getPort();
    this.startMultiSimpleServer(port);
    this.syncClientMultiNoReturn(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "noReturn", "syncHelloWorld:noReturn", 1);
  }

  @Test
  public void syncClientMutiSyncSimpleServerNoReturnParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startMultiSimpleServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncClientMultiNoReturn(port));
    this.waitAndAssertTracesClientSyncServerSync(
        port, "noReturn", "syncHelloWorld:noReturn", threadCount);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerNoReturn() throws TException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    this.syncFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerAsync(port, "noReturn", 1);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerNoReturnParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientNoReturn(port));
    this.waitAndAssertTracesClientSyncServerAsync(port, "noReturn", threadCount);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerNoReturn() throws TException {
    int port = super.getPort();
    this.startMultiThreadedSelectorServer(port);
    this.syncFramedClientMultiNoReturn(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "noReturn", "syncHelloWorld:noReturn", 1);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerNoReturnParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startMultiThreadedSelectorServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientMultiNoReturn(port));
    this.waitAndAssertTracesClientSyncServerSync(
        port, "noReturn", "syncHelloWorld:noReturn", threadCount);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerNoReturn() throws TException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    this.syncFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "noReturn", 1);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerNoReturnParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientNoReturn(port));
    this.waitAndAssertTracesClientSyncServerSync(port, "noReturn", threadCount);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerNoReturn() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    this.nonBlockClientNoReturn(port);
    this.waitAndAssertTracesClientAsyncServerAsync(port, "noReturn", 1);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerNoReturnParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.nonBlockClientNoReturn(port));
    this.waitAndAssertTracesClientAsyncServerAsync(port, "noReturn", threadCount);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerNoReturn() throws TException, IOException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    this.nonBlockClientNoReturn(port);
    this.waitAndAssertTracesClientAsyncServerSync(port, "noReturn", 1);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerNoReturnParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.nonBlockClientNoReturn(port));
    this.waitAndAssertTracesClientAsyncServerSync(port, "noReturn", threadCount);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerNoReturn() throws TException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    this.syncFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "noReturn", 1);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerNoReturnParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientNoReturn(port));
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
  public void syncFramedClientSyncHsHaServerNoReturnParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientNoReturn(port));
    this.waitAndAssertTracesClientSyncServerSync(port, "noReturn", threadCount);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerNoReturn() throws TException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    this.syncFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerAsync(port, "noReturn", 1);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerNoReturnParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientNoReturn(port));
    this.waitAndAssertTracesClientSyncServerAsync(port, "noReturn", threadCount);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerNoReturn() throws TException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    this.syncFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerAsync(port, "noReturn", 1);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerNoReturnParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientNoReturn(port));
    this.waitAndAssertTracesClientSyncServerAsync(port, "noReturn", threadCount);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerNoReturn() throws TException, IOException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    this.nonBlockClientNoReturn(port);
    this.waitAndAssertTracesClientAsyncServerSync(port, "noReturn", 1);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerNoReturnParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.nonBlockClientNoReturn(port));
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
  public void nonBlockClientSyncHsHaServerNoReturnParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.nonBlockClientNoReturn(port));
    this.waitAndAssertTracesClientAsyncServerSync(port, "noReturn", threadCount);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerNoReturn() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    this.nonBlockClientNoReturn(port);
    this.waitAndAssertTracesClientAsyncServerAsync(port, "noReturn", 1);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerNoReturnParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.nonBlockClientNoReturn(port));
    this.waitAndAssertTracesClientAsyncServerAsync(port, "noReturn", threadCount);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerNoReturn() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    this.nonBlockClientNoReturn(port);
    this.waitAndAssertTracesClientAsyncServerAsync(port, "noReturn", 1);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerNoReturnParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.nonBlockClientNoReturn(port));
    this.waitAndAssertTracesClientAsyncServerAsync(port, "noReturn", threadCount);
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

  public void syncClientMultiNoReturn(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      transport.open();
      TProtocol protocol = new TBinaryProtocol(transport);
      TMultiplexedProtocol multiplexedProtocol =
          new TMultiplexedProtocol(protocol, "syncHelloWorld");
      ThriftService.Client client = new ThriftService.Client(multiplexedProtocol);
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
    AsyncMethodCallback<Void> callback =
        new AsyncMethodCallback<Void>() {
          @Override
          public void onComplete(Void s) {}

          @Override
          public void onError(Exception e) {
            assertThat(e.getMessage()).isEqualTo("Read call frame size failed");
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

  public void syncFramedClientMultiNoReturn(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      TFramedTransport framedTransport = new TFramedTransport(transport);
      framedTransport.open();
      TProtocol protocol = new TBinaryProtocol(framedTransport);
      TMultiplexedProtocol multiplexedProtocol =
          new TMultiplexedProtocol(protocol, "syncHelloWorld");
      ThriftService.Client client = new ThriftService.Client(multiplexedProtocol);
      this.testing().runWithSpan("parent", () -> client.noReturn(1));
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }
}
