/*
 * Copyright 2017-2018 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package datadog.trace.instrumentation.aws;

import com.amazonaws.AmazonWebServiceResponse;
import com.amazonaws.Request;
import com.amazonaws.Response;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

class SpanDecorator {
  static final String COMPONENT_NAME = "java-aws-sdk";

  static void onRequest(final Request request, final Span span) {
    Tags.COMPONENT.set(span, COMPONENT_NAME);
    Tags.HTTP_METHOD.set(span, request.getHttpMethod().name());
    Tags.HTTP_URL.set(span, request.getEndpoint().toString());
    span.setTag("aws.agent", COMPONENT_NAME);
    span.setTag("aws.service", request.getServiceName());
    span.setTag("aws.operation", request.getOriginalRequest().getClass().getSimpleName());
    span.setTag("aws.endpoint", request.getEndpoint().toString());

    try {
      StringBuilder params = new StringBuilder("{");
      final Map<String, List<Object>> requestParams = request.getParameters();
      boolean firstKey = true;
      for (Entry<String, List<Object>> entry : requestParams.entrySet()) {
        if (!firstKey) {
          params.append(",");
        }
        params.append(entry.getKey()).append("=[");
        for (int i = 0; i < entry.getValue().size(); ++i) {
          if (i > 0) {
            params.append(",");
          }
          params.append(entry.getValue().get(i));
        }
        params.append("]");
        firstKey = false;
      }
      params.append("}");
      span.setTag("params", params.toString());
    } catch (Exception e) {
      try {
        org.slf4j.LoggerFactory.getLogger(SpanDecorator.class)
            .debug("Failed to decorate aws span", e);
      } catch (Exception e2) {
        // can't reach logger. Silently eat excetpion.
      }
    }
  }

  static void onResponse(final Response response, final Span span) {
    Tags.HTTP_STATUS.set(span, response.getHttpResponse().getStatusCode());
    if (response.getAwsResponse() instanceof AmazonWebServiceResponse) {
      final AmazonWebServiceResponse awsResp = (AmazonWebServiceResponse) response.getAwsResponse();
      span.setTag("aws.requestId", awsResp.getRequestId());
    }
  }

  static void onError(final Throwable throwable, final Span span) {
    Tags.ERROR.set(span, Boolean.TRUE);
    span.log(errorLogs(throwable));
  }

  private static Map<String, Object> errorLogs(final Throwable throwable) {
    final Map<String, Object> errorLogs = new HashMap<>(4);
    errorLogs.put("event", Tags.ERROR.getKey());
    errorLogs.put("error.kind", throwable.getClass().getName());
    errorLogs.put("error.object", throwable);

    errorLogs.put("message", throwable.getMessage());

    final StringWriter sw = new StringWriter();
    throwable.printStackTrace(new PrintWriter(sw));
    errorLogs.put("stack", sw.toString());

    return errorLogs;
  }
}
