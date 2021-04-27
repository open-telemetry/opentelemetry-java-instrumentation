/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle.matcher;

import io.opentelemetry.javaagent.tooling.muzzle.Reference;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * A mismatch between a {@link Reference} and a runtime class.
 *
 * <p>This class' {@link #toString()} returns a human-readable description of the mismatch along
 * with the first source code location of the reference which caused the mismatch.
 */
public abstract class Mismatch {
  /** Instrumentation sources which caused the mismatch. */
  private final Reference.Source[] mismatchSources;

  Mismatch(Reference.Source[] mismatchSources) {
    this.mismatchSources = mismatchSources;
  }

  @Override
  public String toString() {
    if (mismatchSources.length > 0) {
      return mismatchSources[0].toString() + " " + getMismatchDetails();
    } else {
      return "<no-source> " + getMismatchDetails();
    }
  }

  /** Human-readable string describing the mismatch. */
  abstract String getMismatchDetails();

  public static class MissingClass extends Mismatch {
    private final String className;

    public MissingClass(Reference.Source[] sources, String className) {
      super(sources);
      this.className = className;
    }

    @Override
    String getMismatchDetails() {
      return "Missing class " + className;
    }
  }

  public static class MissingFlag extends Mismatch {
    private final Reference.Flag expectedFlag;
    private final String classMethodOrFieldDesc;
    private final int foundAccess;

    public MissingFlag(
        Reference.Source[] sources,
        String classMethodOrFieldDesc,
        Reference.Flag expectedFlag,
        int foundAccess) {
      super(sources);
      this.classMethodOrFieldDesc = classMethodOrFieldDesc;
      this.expectedFlag = expectedFlag;
      this.foundAccess = foundAccess;
    }

    @Override
    String getMismatchDetails() {
      return classMethodOrFieldDesc + " requires flag " + expectedFlag + " found " + foundAccess;
    }
  }

  public static class MissingField extends Mismatch {
    private final String className;
    private final String fieldName;
    private final String fieldDesc;

    public MissingField(
        Reference.Source[] sources, String className, String fieldName, String fieldDesc) {
      super(sources);
      this.className = className;
      this.fieldName = fieldName;
      this.fieldDesc = fieldDesc;
    }

    @Override
    String getMismatchDetails() {
      return "Missing field " + className + "#" + fieldName + fieldDesc;
    }
  }

  public static class MissingMethod extends Mismatch {
    private final String className;
    private final String methodName;
    private final String methodDescriptor;

    public MissingMethod(
        Reference.Source[] sources, String className, String methodName, String methodDescriptor) {
      super(sources);
      this.className = className;
      this.methodName = methodName;
      this.methodDescriptor = methodDescriptor;
    }

    @Override
    String getMismatchDetails() {
      return "Missing method " + className + "#" + methodName + methodDescriptor;
    }
  }

  /** Fallback mismatch in case an unexpected exception occurs during reference checking. */
  public static class ReferenceCheckError extends Mismatch {
    private final Exception referenceCheckException;
    private final Reference referenceBeingChecked;
    private final ClassLoader classLoaderBeingChecked;

    public ReferenceCheckError(
        Exception e, Reference referenceBeingChecked, ClassLoader classLoaderBeingChecked) {
      super(new Reference.Source[0]);
      referenceCheckException = e;
      this.referenceBeingChecked = referenceBeingChecked;
      this.classLoaderBeingChecked = classLoaderBeingChecked;
    }

    @Override
    String getMismatchDetails() {
      StringWriter sw = new StringWriter();
      sw.write("Failed to generate reference check for: ");
      sw.write(referenceBeingChecked.toString());
      sw.write(" on classloader ");
      sw.write(classLoaderBeingChecked.toString());
      sw.write("\n");
      // add exception message and stack trace
      PrintWriter pw = new PrintWriter(sw);
      referenceCheckException.printStackTrace(pw);
      return sw.toString();
    }
  }
}
