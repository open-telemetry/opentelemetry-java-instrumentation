package io.opentelemetry.smoketest.fakebackend;

import com.google.protobuf.InvalidProtocolBufferException;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.ServiceRequestContext;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;

public class FakeTraceCollectorServiceHttp implements HttpService {

  private final RequestsStorage storage;

  public FakeTraceCollectorServiceHttp(RequestsStorage storage) {this.storage = storage;}

  @Override
  public HttpResponse serve(ServiceRequestContext ctx, HttpRequest request) throws Exception {
    return HttpResponse.of(
        request
            .aggregate()
            .thenApply(
                aggregatedRequest -> {
                  try {
                    storage.addTrace(
                        ExportTraceServiceRequest.parseFrom(
                            aggregatedRequest.content().array()));
                    return HttpResponse.of(
                        HttpStatus.OK,
                        MediaType.X_PROTOBUF,
                        ExportTraceServiceResponse.getDefaultInstance().toByteArray());
                  } catch (InvalidProtocolBufferException e) {
                    return HttpResponse.of(
                        HttpStatus.BAD_REQUEST, MediaType.PLAIN_TEXT_UTF_8, e.getMessage());
                  }
                }));
  }
}
