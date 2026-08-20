/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.fakebackend;

import com.google.common.collect.ImmutableList;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceResponse;
import io.opentelemetry.proto.collector.logs.v1.LogsServiceGrpc;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

public class FakeLogsCollectorService extends LogsServiceGrpc.LogsServiceImplBase {

  private final RequestsStorage storage;

  public FakeLogsCollectorService(RequestsStorage storage) {
    this.storage = storage;
  }

  List<ExportLogsServiceRequest> getRequests() {
    return storage.getLogsRequests();
  }

  void clearRequests() {
    storage.clearLogs();
  }

  @Override
  public void export(
      ExportLogsServiceRequest request,
      StreamObserver<ExportLogsServiceResponse> responseObserver) {
    storage.addLogsRequest(request);
    responseObserver.onNext(ExportLogsServiceResponse.getDefaultInstance());
    responseObserver.onCompleted();
  }
}
