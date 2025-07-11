/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

/**
 * Holder for {@link LambdaTransformer} used by the instrumentation. Calling transform on this
 * transformer processes given bytes the same way as they would be processed during loading of the
 * class.
 */
public final class LambdaTransformerHolder {

  private static volatile LambdaTransformer lambdaTransformer;

  /**
   * get lambda transformer
   *
   * @return class transformer for defining lambdas
   */
  public static LambdaTransformer getLambdaTransformer() {
    return lambdaTransformer;
  }

  /**
   * set lambda transformer
   *
   * @param transformer transformer
   */
  public static void setLambdaTransformer(LambdaTransformer transformer) {
    lambdaTransformer = transformer;
  }

  private LambdaTransformerHolder() {}
}
