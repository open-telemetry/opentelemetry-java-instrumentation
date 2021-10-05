/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.grpc;

import io.grpc.stub.StreamObserver;
import io.opentelemetry.extension.annotations.WithSpan;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TestService extends TraceServiceGrpc.TraceServiceImplBase {

  private static final Logger logger = LogManager.getLogger();

  @Override
  public void export(
      ExportTraceServiceRequest request,
      StreamObserver<ExportTraceServiceResponse> responseObserver) {
    logger.info("Request received");
    withSpan();
    responseObserver.onNext(ExportTraceServiceResponse.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @WithSpan
  public String withSpan() {
    return "Hi";
  }
}
