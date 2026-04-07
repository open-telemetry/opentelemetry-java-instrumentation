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

public class OneWayErrorTest extends ThriftBaseTest {

  @Test
  public void syncClientSyncSimpleServerOneWayWithError() throws TException {
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    this.syncClientOneWayWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(port, "oneWayWithError", 1);
  }

  @Test
  public void syncClientSyncSimpleServerOneWayWithErrorParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncClientOneWayWithError(port));
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(port, "oneWayWithError", threadCount);
  }

  @Test
  public void syncClientSyncThreadPoolServerOneWayWithError()
      throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    this.syncClientOneWayWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(port, "oneWayWithError", 1);
  }

  @Test
  public void syncClientSyncThreadPoolServerOneWayWithErrorParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncClientOneWayWithError(port));
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(port, "oneWayWithError", threadCount);
  }

  @Test
  public void syncClientMutiSyncSimpleServerOneWayWithError() throws TException {
    int port = super.getPort();
    this.startMultiSimpleServer(port);
    this.syncClientMultiOneWayWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(
        port, "oneWayWithError", "syncHelloWorld:oneWayWithError", 1);
  }

  @Test
  public void syncClientMutiSyncSimpleServerOneWayWithErrorParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startMultiSimpleServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncClientMultiOneWayWithError(port));
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(
        port, "oneWayWithError", "syncHelloWorld:oneWayWithError", threadCount);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerOneWayWithError() throws TException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    this.syncFramedClientOneWayWithError(port);
    this.waitAndAssertTracesClientSyncServerAsyncError(port, "oneWayWithError", 1);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerOneWayWithErrorParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientOneWayWithError(port));
    this.waitAndAssertTracesClientSyncServerAsyncError(port, "oneWayWithError", threadCount);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerOneWayWithError() throws TException {
    int port = super.getPort();
    this.startMultiThreadedSelectorServer(port);
    this.syncFramedClientMultiOneWayWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(
        port, "oneWayWithError", "syncHelloWorld:oneWayWithError", 1);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerOneWayWithErrorParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startMultiThreadedSelectorServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientMultiOneWayWithError(port));
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(
        port, "oneWayWithError", "syncHelloWorld:oneWayWithError", threadCount);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerOneWayWithError() throws TException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    this.syncFramedClientOneWayWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(port, "oneWayWithError", 1);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerOneWayWithErrorParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientOneWayWithError(port));
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(port, "oneWayWithError", threadCount);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerOneWayWithError()
      throws TException, IOException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    this.nonBlockClientOneWayWithError(port);
    this.waitAndAssertTracesClientAsyncServerAsyncError(port, "oneWayWithError", 1);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerOneWayWithErrorParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.nonBlockClientOneWayWithError(port));
    this.waitAndAssertTracesClientAsyncServerAsyncError(port, "oneWayWithError", threadCount);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerOneWayWithError()
      throws TException, IOException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    this.nonBlockClientOneWayWithError(port);
    this.waitAndAssertTracesClientAsyncServerSyncOnewayError(port, "oneWayWithError", 1);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerOneWayWithErrorParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.nonBlockClientOneWayWithError(port));
    this.waitAndAssertTracesClientAsyncServerSyncOnewayError(port, "oneWayWithError", threadCount);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerOneWayWithError() throws TException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    this.syncFramedClientOneWayWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(port, "oneWayWithError", 1);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerOneWayWithErrorParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientOneWayWithError(port));
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(port, "oneWayWithError", threadCount);
  }

  @Test
  public void syncFramedClientSyncHsHaServerOneWayWithError() throws TException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    this.syncFramedClientOneWayWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(port, "oneWayWithError", 1);
  }

  @Test
  public void syncFramedClientSyncHsHaServerOneWayWithErrorParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientOneWayWithError(port));
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(port, "oneWayWithError", threadCount);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerOneWayWithError() throws TException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    this.syncFramedClientOneWayWithError(port);
    this.waitAndAssertTracesClientSyncServerAsyncError(port, "oneWayWithError", 1);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerOneWayWithErrorParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientOneWayWithError(port));
    this.waitAndAssertTracesClientSyncServerAsyncError(port, "oneWayWithError", threadCount);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerOneWayWithError() throws TException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    this.syncFramedClientOneWayWithError(port);
    this.waitAndAssertTracesClientSyncServerAsyncError(port, "oneWayWithError", 1);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerOneWayWithErrorParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientOneWayWithError(port));
    this.waitAndAssertTracesClientSyncServerAsyncError(port, "oneWayWithError", threadCount);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerOneWayWithError() throws TException, IOException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    this.nonBlockClientOneWayWithError(port);
    this.waitAndAssertTracesClientAsyncServerSyncOnewayError(port, "oneWayWithError", 1);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerOneWayWithErrorParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.nonBlockClientOneWayWithError(port));
    this.waitAndAssertTracesClientAsyncServerSyncOnewayError(port, "oneWayWithError", threadCount);
  }

  @Test
  public void nonBlockClientSyncHsHaServerOneWayWithError() throws TException, IOException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    this.nonBlockClientOneWayWithError(port);
    this.waitAndAssertTracesClientAsyncServerSyncOnewayError(port, "oneWayWithError", 1);
  }

  @Test
  public void nonBlockClientSyncHsHaServerOneWayWithErrorParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.nonBlockClientOneWayWithError(port));
    this.waitAndAssertTracesClientAsyncServerSyncOnewayError(port, "oneWayWithError", threadCount);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerOneWayWithError() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    this.nonBlockClientOneWayWithError(port);
    this.waitAndAssertTracesClientAsyncServerAsyncError(port, "oneWayWithError", 1);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerOneWayWithErrorParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.nonBlockClientOneWayWithError(port));
    this.waitAndAssertTracesClientAsyncServerAsyncError(port, "oneWayWithError", threadCount);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerOneWayWithError() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    this.nonBlockClientOneWayWithError(port);
    this.waitAndAssertTracesClientAsyncServerAsyncError(port, "oneWayWithError", 1);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerOneWayWithErrorParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.nonBlockClientOneWayWithError(port));
    this.waitAndAssertTracesClientAsyncServerAsyncError(port, "oneWayWithError", threadCount);
  }

  public void syncClientOneWayWithError(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      transport.open();
      TProtocol protocol = new TBinaryProtocol(transport);
      ThriftService.Client client = new ThriftService.Client(protocol);
      this.testing().runWithSpan("parent", () -> client.oneWayWithError());
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void syncClientMultiOneWayWithError(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      transport.open();
      TProtocol protocol = new TBinaryProtocol(transport);
      TMultiplexedProtocol multiplexedProtocol =
          new TMultiplexedProtocol(protocol, "syncHelloWorld");
      ThriftService.Client client = new ThriftService.Client(multiplexedProtocol);
      this.testing().runWithSpan("parent", () -> client.oneWayWithError());
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void nonBlockClientOneWayWithError(int port) throws TException, IOException {
    TNonblockingTransport transport = new TNonblockingSocket("localhost", port);
    TAsyncClientManager clientManager = new TAsyncClientManager();
    TProtocolFactory protocolFactory = new TBinaryProtocol.Factory();
    ThriftService.AsyncClient.Factory factory =
        new ThriftService.AsyncClient.Factory(clientManager, protocolFactory);
    ThriftService.AsyncClient asyClient = factory.getAsyncClient(transport);
    AsyncMethodCallback<Void> callback =
        new AsyncMethodCallback<Void>() {
          @Override
          public void onComplete(Void no) {}

          @Override
          public void onError(Exception e) {
            assertThat(e.getMessage()).isEqualTo("Read call frame size failed");
          }
        };
    this.testing().runWithSpan("parent", () -> asyClient.oneWayWithError(callback));
  }

  public void syncFramedClientOneWayWithError(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      TFramedTransport framedTransport = new TFramedTransport(transport);
      framedTransport.open();
      TProtocol protocol = new TBinaryProtocol(framedTransport);
      ThriftService.Client client = new ThriftService.Client(protocol);
      this.testing().runWithSpan("parent", () -> client.oneWayWithError());
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void syncFramedClientMultiOneWayWithError(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      TFramedTransport framedTransport = new TFramedTransport(transport);
      framedTransport.open();
      TProtocol protocol = new TBinaryProtocol(framedTransport);
      TMultiplexedProtocol multiplexedProtocol =
          new TMultiplexedProtocol(protocol, "syncHelloWorld");
      ThriftService.Client client = new ThriftService.Client(multiplexedProtocol);
      this.testing().runWithSpan("parent", () -> client.oneWayWithError());
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }
}
