/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.fakebackend;

import io.grpc.stub.StreamObserver;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceResponse;
import io.opentelemetry.proto.collector.logs.v1.LogsServiceGrpc;

public class FakeLogsCollectorServiceGrpc extends LogsServiceGrpc.LogsServiceImplBase {

  private final RequestsStorage storage;

  public FakeLogsCollectorServiceGrpc(RequestsStorage storage) {
    this.storage = storage;
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
