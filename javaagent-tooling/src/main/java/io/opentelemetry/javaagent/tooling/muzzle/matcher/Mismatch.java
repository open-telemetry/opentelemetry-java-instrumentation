/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle.matcher;

import io.opentelemetry.javaagent.extension.muzzle.Reference;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Collections;
import net.bytebuddy.jar.asm.Type;

/**
 * A mismatch between a {@link Reference} and a runtime class.
 *
 * <p>This class' {@link #toString()} returns a human-readable description of the mismatch along
 * with the first source code location of the reference which caused the mismatch.
 */
public abstract class Mismatch {
  /** Instrumentation sources which caused the mismatch. */
  private final Collection<Reference.Source> mismatchSources;

  private Mismatch(Collection<Reference.Source> mismatchSources) {
    this.mismatchSources = mismatchSources;
  }

  @Override
  public String toString() {
    if (mismatchSources.size() > 0) {
      return mismatchSources.iterator().next().toString() + " " + getMismatchDetails();
    } else {
      return "<no-source> " + getMismatchDetails();
    }
  }

  /** Human-readable string describing the mismatch. */
  abstract String getMismatchDetails();

  public static class MissingClass extends Mismatch {
    private final String className;

    public MissingClass(Reference classRef) {
      super(classRef.getSources());
      this.className = classRef.getClassName();
    }

    public MissingClass(Reference classRef, String className) {
      super(classRef.getSources());
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
        Collection<Reference.Source> sources,
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
    private final String fieldDescriptor;

    MissingField(Reference classRef, Reference.Field fieldRef) {
      super(fieldRef.getSources());
      this.className = classRef.getClassName();
      this.fieldName = fieldRef.getName();
      this.fieldDescriptor = fieldRef.getDescriptor();
    }

    MissingField(Reference classRef, HelperReferenceWrapper.Field field) {
      super(classRef.getSources());
      this.className = classRef.getClassName();
      this.fieldName = field.getName();
      this.fieldDescriptor = field.getDescriptor();
    }

    @Override
    String getMismatchDetails() {
      return "Missing field "
          + Type.getType(fieldDescriptor).getClassName()
          + " "
          + fieldName
          + " in class "
          + className;
    }
  }

  public static class MissingMethod extends Mismatch {
    private final String className;
    private final String methodName;
    private final String methodDescriptor;

    public MissingMethod(Reference classRef, Reference.Method methodRef) {
      super(methodRef.getSources());
      this.className = classRef.getClassName();
      this.methodName = methodRef.getName();
      this.methodDescriptor = methodRef.getDescriptor();
    }

    public MissingMethod(Reference classRef, HelperReferenceWrapper.Method method) {
      super(classRef.getSources());
      this.className = method.getDeclaringClass();
      this.methodName = method.getName();
      this.methodDescriptor = method.getDescriptor();
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
      super(Collections.emptyList());
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
