/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.awslambda.v1_0;

import io.opentelemetry.javaagent.tooling.Instrumenter;

public abstract class AbstractAwsLambdaInstrumentation extends Instrumenter.Default {

  public AbstractAwsLambdaInstrumentation() {
    super("aws-lambda");
  }

  @Override
  public final String[] helperClassNames() {
    return new String[] {
      packageName + ".AwsLambdaInstrumentationHelper",
      "io.opentelemetry.instrumentation.awslambda.v1_0.AwsLambdaTracer",
      "io.opentelemetry.instrumentation.awslambda.v1_0.AwsLambdaMessageTracer",
      "io.opentelemetry.instrumentation.awslambda.v1_0.AwsLambdaMessageTracer$MapGetter"
    };
  }
}
