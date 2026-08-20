/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.fakebackend;

import com.google.common.collect.ImmutableList;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceResponse;
import io.opentelemetry.proto.collector.metrics.v1.MetricsServiceGrpc;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

class FakeMetricsCollectorService extends MetricsServiceGrpc.MetricsServiceImplBase {

  private final RequestsStorage storage;

  public FakeMetricsCollectorService(RequestsStorage storage) {
    this.storage = storage;
  }

  List<ExportMetricsServiceRequest> getRequests() {
    return storage.getMetricsRequests();
  }

  void clearRequests() {
    storage.clearMetrics();
  }

  @Override
  public void export(
      ExportMetricsServiceRequest request,
      StreamObserver<ExportMetricsServiceResponse> responseObserver) {
    storage.addMetricsRequest(request);
    responseObserver.onNext(ExportMetricsServiceResponse.getDefaultInstance());
    responseObserver.onCompleted();
  }
}
