/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package library;

/**
 * A class which will not be transformed by our instrumentation due to, see
 * FieldBackedProviderTest's skipTransformationConditions() method.
 */
public class UntransformableKeyClass extends KeyClass {
  @Override
  public boolean isInstrumented() {
    return false;
  }
}
