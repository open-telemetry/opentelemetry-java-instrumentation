/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import com.amazonaws.AmazonWebServiceResult;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.ResponseMetadata;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import javax.annotation.Nullable;

class AwsSdkAttributesExtractor implements AttributesExtractor<Request<?>, Response<?>> {
  private static final boolean CAN_GET_RESPONSE_METADATA = canGetResponseMetadata();
  private static final AttributeKey<String> AWS_REQUEST_ID = stringKey("aws.request_id");

  // AmazonWebServiceResult is only available in v1.11.33 and later
  private static boolean canGetResponseMetadata() {
    try {
      Class<?> clazz = Class.forName("com.amazonaws.AmazonWebServiceResult");
      clazz.getMethod("getSdkResponseMetadata");
      return true;
    } catch (ClassNotFoundException | NoSuchMethodException exception) {
      return false;
    }
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, Request<?> request) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      Request<?> request,
      @Nullable Response<?> response,
      @Nullable Throwable error) {
    ResponseMetadata responseMetadata = getResponseMetadata(response);

    if (responseMetadata != null) {
      String requestId = responseMetadata.getRequestId();
      if (requestId != null) {
        attributes.put(AWS_REQUEST_ID, requestId);
      }
    }
  }

  @NoMuzzle
  private static ResponseMetadata getResponseMetadata(Response<?> response) {
    if (CAN_GET_RESPONSE_METADATA
        && response != null
        && response.getAwsResponse() instanceof AmazonWebServiceResult) {
      AmazonWebServiceResult<?> awsResp = (AmazonWebServiceResult<?>) response.getAwsResponse();
      return awsResp.getSdkResponseMetadata();
    }
    return null;
  }
}
