/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.universal;

/**
 * Represents a tuple configuration for instrumentation targets in the universal Vertx context
 * persistence system.
 *
 * <p>Each target defines: - packageName: The package containing the target class - className: The
 * class name to instrument - methodName: The method name that accepts a Handler parameter -
 * numberOfArgs: Total number of arguments in the method - handlerArgIndex: Zero-based index of the
 * Handler argument - classType: The type of class (CONCRETE, ABSTRACT, INTERFACE) - isPrivate:
 * Whether the method is private (requires special instrumentation matcher)
 */
public final class InstrumentationTarget {
  private final String packageName;
  private final String className;
  private final String methodName;
  private final int numberOfArgs;
  private final int handlerArgIndex;
  private final ClassType classType;
  private final boolean isPrivate;

  public InstrumentationTarget(
      String packageName,
      String className,
      String methodName,
      int numberOfArgs,
      int handlerArgIndex,
      ClassType classType,
      boolean isPrivate) {
    this.packageName = packageName;
    this.className = className;
    this.methodName = methodName;
    this.numberOfArgs = numberOfArgs;
    this.handlerArgIndex = handlerArgIndex;
    this.classType = classType;
    this.isPrivate = isPrivate;
  }

  public String getPackageName() {
    return packageName;
  }

  public String getClassName() {
    return className;
  }

  public String getMethodName() {
    return methodName;
  }

  public int getNumberOfArgs() {
    return numberOfArgs;
  }

  public int getHandlerArgIndex() {
    return handlerArgIndex;
  }

  public ClassType getClassType() {
    return classType;
  }

  public boolean isPrivate() {
    return isPrivate;
  }

  public String getFullClassName() {
    return packageName + "." + className;
  }

  @Override
  public String toString() {
    return String.format(
        java.util.Locale.ROOT,
        "InstrumentationTarget{%s.%s.%s(%d args, handler at %d, %s, %s)}",
        packageName,
        className,
        methodName,
        numberOfArgs,
        handlerArgIndex,
        classType,
        isPrivate ? "PRIVATE" : "PUBLIC/PACKAGE");
  }
}
