/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.fakebackend;

import com.google.protobuf.InvalidProtocolBufferException;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.ServiceRequestContext;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceResponse;

public class FakeLogsCollectorServiceHttp implements HttpService {

  private final RequestsStorage storage;

  public FakeLogsCollectorServiceHttp(RequestsStorage storage) {
    this.storage = storage;
  }

  @Override
  public HttpResponse serve(ServiceRequestContext ctx, HttpRequest request) throws Exception {
    return HttpResponse.of(
        request
            .aggregate()
            .thenApply(
                aggregatedRequest -> {
                  try {
                    storage.addLogsRequest(
                        ExportLogsServiceRequest.parseFrom(aggregatedRequest.content().array()));
                    return HttpResponse.of(
                        HttpStatus.OK,
                        MediaType.X_PROTOBUF,
                        ExportLogsServiceResponse.getDefaultInstance().toByteArray());
                  } catch (InvalidProtocolBufferException e) {
                    return HttpResponse.of(
                        HttpStatus.BAD_REQUEST, MediaType.PLAIN_TEXT_UTF_8, e.getMessage());
                  }
                }));
  }
}
