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

  private final BlockingQueue<ExportMetricsServiceRequest> exportRequests =
      new LinkedBlockingDeque<>();

  List<ExportMetricsServiceRequest> getRequests() {
    return ImmutableList.copyOf(exportRequests);
  }

  void clearRequests() {
    exportRequests.clear();
  }

  @Override
  public void export(
      ExportMetricsServiceRequest request,
      StreamObserver<ExportMetricsServiceResponse> responseObserver) {
    exportRequests.add(request);
    responseObserver.onNext(ExportMetricsServiceResponse.getDefaultInstance());
    responseObserver.onCompleted();
  }
}
