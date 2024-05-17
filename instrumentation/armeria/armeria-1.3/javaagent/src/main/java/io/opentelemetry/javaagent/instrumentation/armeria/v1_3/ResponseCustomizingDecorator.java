/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.v1_3;

import com.linecorp.armeria.common.FilteredHttpResponse;
import com.linecorp.armeria.common.HttpObject;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.common.ResponseHeadersBuilder;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.SimpleDecoratingHttpService;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseCustomizerHolder;

class ResponseCustomizingDecorator extends SimpleDecoratingHttpService {

  ResponseCustomizingDecorator(HttpService delegate) {
    super(delegate);
  }

  @Override
  public HttpResponse serve(ServiceRequestContext ctx, HttpRequest req) throws Exception {
    HttpResponse response = unwrap().serve(ctx, req);
    Context context = Context.current();
    return new FilteredHttpResponse(response) {
      @Override
      public HttpObject filter(HttpObject obj) {
        // Ignore other objects like HttpData.
        if (!(obj instanceof ResponseHeaders)) {
          return obj;
        }

        ResponseHeaders headers = (ResponseHeaders) obj;
        ResponseHeadersBuilder headersBuilder = headers.toBuilder();
        HttpServerResponseCustomizerHolder.getCustomizer()
            .customize(context, headersBuilder, ArmeriaHttpResponseMutator.INSTANCE);

        return headersBuilder.build();
      }
    };
  }
}
