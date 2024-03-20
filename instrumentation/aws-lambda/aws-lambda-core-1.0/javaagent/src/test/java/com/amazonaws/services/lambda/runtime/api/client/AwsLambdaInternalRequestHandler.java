/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazonaws.services.lambda.runtime.api.client;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

public class AwsLambdaInternalRequestHandler implements RequestHandler<String, String> {

  private final RequestHandler<String, String> requestHandler;

  public AwsLambdaInternalRequestHandler(RequestHandler<String, String> requestHandler) {
    this.requestHandler = requestHandler;
  }

  @Override
  public String handleRequest(String input, Context context) {
    return requestHandler.handleRequest(input, context);
  }
}
