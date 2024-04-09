/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package lambdainternal;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

public class AwsLambdaLegacyInternalRequestHandler implements RequestHandler<String, String> {

  private final RequestHandler<String, String> requestHandler;

  public AwsLambdaLegacyInternalRequestHandler(RequestHandler<String, String> requestHandler) {
    this.requestHandler = requestHandler;
  }

  @Override
  public String handleRequest(String input, Context context) {
    return requestHandler.handleRequest(input, context);
  }
}
