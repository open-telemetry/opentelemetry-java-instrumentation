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

public class OneWayTest extends ThriftBaseTest {

  @Test
  public void syncClientSyncSimpleServerOneWay() throws TException {
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    this.syncClientOneWay(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "oneWay", 1);
  }

  @Test
  public void syncClientSyncSimpleServerOneWayParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncClientOneWay(port));
    this.waitAndAssertTracesClientSyncServerSync(port, "oneWay", threadCount);
  }

  @Test
  public void syncClientSyncThreadPoolServerOneWay() throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    this.syncClientOneWay(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "oneWay", 1);
  }

  @Test
  public void syncClientSyncThreadPoolServerOneWayParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncClientOneWay(port));
    this.waitAndAssertTracesClientSyncServerSync(port, "oneWay", threadCount);
  }

  @Test
  public void syncClientMutiSyncSimpleServerOneWay() throws TException {
    int port = super.getPort();
    this.startMultiSimpleServer(port);
    this.syncClientMultiOneWay(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "oneWay", "syncHelloWorld:oneWay", 1);
  }

  @Test
  public void syncClientMutiSyncSimpleServerOneWayParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startMultiSimpleServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncClientMultiOneWay(port));
    this.waitAndAssertTracesClientSyncServerSync(
        port, "oneWay", "syncHelloWorld:oneWay", threadCount);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerOneWay() throws TException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    this.syncFramedClientOneWay(port);
    this.waitAndAssertTracesClientSyncServerAsync(port, "oneWay", 1);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerOneWayParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientOneWay(port));
    this.waitAndAssertTracesClientSyncServerAsync(port, "oneWay", threadCount);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerOneWay() throws TException {
    int port = super.getPort();
    this.startMultiThreadedSelectorServer(port);
    this.syncFramedClientMultiOneWay(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "oneWay", "syncHelloWorld:oneWay", 1);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerOneWayParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startMultiThreadedSelectorServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientMultiOneWay(port));
    this.waitAndAssertTracesClientSyncServerSync(
        port, "oneWay", "syncHelloWorld:oneWay", threadCount);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerOneWay() throws TException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    this.syncFramedClientOneWay(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "oneWay", 1);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerOneWayParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientOneWay(port));
    this.waitAndAssertTracesClientSyncServerSync(port, "oneWay", threadCount);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerOneWay() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    this.nonBlockClientOneWay(port);
    this.waitAndAssertTracesClientAsyncServerAsync(port, "oneWay", 1);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerOneWayParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.nonBlockClientOneWay(port));
    this.waitAndAssertTracesClientAsyncServerAsync(port, "oneWay", threadCount);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerOneWay() throws TException, IOException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    this.nonBlockClientOneWay(port);
    this.waitAndAssertTracesClientAsyncServerSync(port, "oneWay", 1);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerOneWayParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.nonBlockClientOneWay(port));
    this.waitAndAssertTracesClientAsyncServerSync(port, "oneWay", threadCount);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerOneWay() throws TException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    this.syncFramedClientOneWay(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "oneWay", 1);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerOneWayParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientOneWay(port));
    this.waitAndAssertTracesClientSyncServerSync(port, "oneWay", threadCount);
  }

  @Test
  public void syncFramedClientSyncHsHaServerOneWay() throws TException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    this.syncFramedClientOneWay(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "oneWay", 1);
  }

  @Test
  public void syncFramedClientSyncHsHaServerOneWayParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientOneWay(port));
    this.waitAndAssertTracesClientSyncServerSync(port, "oneWay", threadCount);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerOneWay() throws TException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    this.syncFramedClientOneWay(port);
    this.waitAndAssertTracesClientSyncServerAsync(port, "oneWay", 1);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerOneWayParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientOneWay(port));
    this.waitAndAssertTracesClientSyncServerAsync(port, "oneWay", threadCount);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerOneWay() throws TException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    this.syncFramedClientOneWay(port);
    this.waitAndAssertTracesClientSyncServerAsync(port, "oneWay", 1);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerOneWayParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientOneWay(port));
    this.waitAndAssertTracesClientSyncServerAsync(port, "oneWay", threadCount);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerOneWay() throws TException, IOException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    this.nonBlockClientOneWay(port);
    this.waitAndAssertTracesClientAsyncServerSync(port, "oneWay", 1);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerOneWayParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.nonBlockClientOneWay(port));
    this.waitAndAssertTracesClientAsyncServerSync(port, "oneWay", threadCount);
  }

  @Test
  public void nonBlockClientSyncHsHaServerOneWay() throws TException, IOException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    this.nonBlockClientOneWay(port);
    this.waitAndAssertTracesClientAsyncServerSync(port, "oneWay", 1);
  }

  @Test
  public void nonBlockClientSyncHsHaServerOneWayParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.nonBlockClientOneWay(port));
    this.waitAndAssertTracesClientAsyncServerSync(port, "oneWay", threadCount);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerOneWay() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    this.nonBlockClientOneWay(port);
    this.waitAndAssertTracesClientAsyncServerAsync(port, "oneWay", 1);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerOneWayParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.nonBlockClientOneWay(port));
    this.waitAndAssertTracesClientAsyncServerAsync(port, "oneWay", threadCount);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerOneWay() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    this.nonBlockClientOneWay(port);
    this.waitAndAssertTracesClientAsyncServerAsync(port, "oneWay", 1);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerOneWayParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.nonBlockClientOneWay(port));
    this.waitAndAssertTracesClientAsyncServerAsync(port, "oneWay", threadCount);
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

  public void syncClientMultiOneWay(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      transport.open();
      TProtocol protocol = new TBinaryProtocol(transport);
      TMultiplexedProtocol multiplexedProtocol =
          new TMultiplexedProtocol(protocol, "syncHelloWorld");
      ThriftService.Client client = new ThriftService.Client(multiplexedProtocol);
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
    AsyncMethodCallback<Void> callback =
        new AsyncMethodCallback<Void>() {
          @Override
          public void onComplete(Void result) {}

          @Override
          public void onError(Exception e) {
            assertThat(e.getMessage()).isEqualTo("Read call frame size failed");
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

  public void syncFramedClientMultiOneWay(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      TFramedTransport framedTransport = new TFramedTransport(transport);
      framedTransport.open();
      TProtocol protocol = new TBinaryProtocol(framedTransport);
      TMultiplexedProtocol multiplexedProtocol =
          new TMultiplexedProtocol(protocol, "syncHelloWorld");
      ThriftService.Client client = new ThriftService.Client(multiplexedProtocol);
      this.testing().runWithSpan("parent", () -> client.oneWay());
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }
}
