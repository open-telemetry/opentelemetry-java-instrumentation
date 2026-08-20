/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.fakebackend;

import io.grpc.stub.StreamObserver;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc;
import java.util.List;

class FakeTraceCollectorService extends TraceServiceGrpc.TraceServiceImplBase {

  private final RequestsStorage requestsStorage;

  FakeTraceCollectorService(
      RequestsStorage requestsStorage) {this.requestsStorage = requestsStorage;}

  List<ExportTraceServiceRequest> getRequests() {
    return requestsStorage.getTraceRequests();
  }

  void clearRequests() {
    requestsStorage.clearTraces();
  }

  @Override
  public void export(
      ExportTraceServiceRequest request,
      StreamObserver<ExportTraceServiceResponse> responseObserver) {
    requestsStorage.addTrace(request);
    responseObserver.onNext(ExportTraceServiceResponse.getDefaultInstance());
    responseObserver.onCompleted();
  }
}
