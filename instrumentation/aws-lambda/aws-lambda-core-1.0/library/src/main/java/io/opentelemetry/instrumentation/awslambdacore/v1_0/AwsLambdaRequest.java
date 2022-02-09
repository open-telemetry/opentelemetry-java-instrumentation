/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0;

import com.amazonaws.services.lambda.runtime.Context;
import com.google.auto.value.AutoValue;
import java.util.Map;

@AutoValue
public abstract class AwsLambdaRequest {

  public static AwsLambdaRequest create(
      Context awsContext, Object input, Map<String, String> headers) {
    return new AutoValue_AwsLambdaRequest(awsContext, input, headers);
  }

  public abstract Context getAwsContext();

  public abstract Object getInput();

  public abstract Map<String, String> getHeaders();
}
