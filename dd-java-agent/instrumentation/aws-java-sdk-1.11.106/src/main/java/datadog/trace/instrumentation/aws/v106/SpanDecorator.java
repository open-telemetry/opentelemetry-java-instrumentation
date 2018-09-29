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
package datadog.trace.instrumentation.aws.v106;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import com.amazonaws.AmazonWebServiceResponse;
import com.amazonaws.Request;
import com.amazonaws.Response;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class SpanDecorator {
  static final String COMPONENT_NAME = "java-aws-sdk";

  private static final Map<String, String> SERVICE_NAMES = new ConcurrentHashMap<>();
  private static final Map<Class, String> OPERATION_NAMES = new ConcurrentHashMap<>();

  static void onRequest(final Request<?> request, final Span span) {
    Tags.COMPONENT.set(span, COMPONENT_NAME);
    Tags.HTTP_METHOD.set(span, request.getHttpMethod().name());
    Tags.HTTP_URL.set(span, request.getEndpoint().toString());

    final String awsServiceName = request.getServiceName();
    final Class<?> awsOperation = request.getOriginalRequest().getClass();

    span.setTag("aws.agent", COMPONENT_NAME);
    span.setTag("aws.service", awsServiceName);
    span.setTag("aws.operation", awsOperation.getSimpleName());
    span.setTag("aws.endpoint", request.getEndpoint().toString());

    span.setTag(DDTags.SERVICE_NAME, COMPONENT_NAME);
    span.setTag(
        DDTags.RESOURCE_NAME,
        remapServiceName(awsServiceName) + "." + remapOperationName(awsOperation));
    span.setTag(DDTags.SPAN_TYPE, DDSpanTypes.HTTP_CLIENT);
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
    span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
  }

  private static String remapServiceName(final String serviceName) {
    if (!SERVICE_NAMES.containsKey(serviceName)) {
      SERVICE_NAMES.put(serviceName, serviceName.replace("Amazon", "").trim());
    }
    return SERVICE_NAMES.get(serviceName);
  }

  private static String remapOperationName(final Class<?> awsOperation) {
    if (!OPERATION_NAMES.containsKey(awsOperation)) {
      OPERATION_NAMES.put(awsOperation, awsOperation.getSimpleName().replace("Request", ""));
    }
    return OPERATION_NAMES.get(awsOperation);
  }
}
