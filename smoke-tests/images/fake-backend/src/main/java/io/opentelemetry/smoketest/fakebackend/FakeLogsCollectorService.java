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

  private final BlockingQueue<ExportLogsServiceRequest> exportRequests =
      new LinkedBlockingDeque<>();

  List<ExportLogsServiceRequest> getRequests() {
    return ImmutableList.copyOf(exportRequests);
  }

  void clearRequests() {
    exportRequests.clear();
  }

  @Override
  public void export(
      ExportLogsServiceRequest request,
      StreamObserver<ExportLogsServiceResponse> responseObserver) {
    exportRequests.add(request);
    responseObserver.onNext(ExportLogsServiceResponse.getDefaultInstance());
    responseObserver.onCompleted();
  }
}
