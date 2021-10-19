/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.muzzle.references.ClassRef;
import io.opentelemetry.javaagent.tooling.muzzle.references.FieldRef;
import io.opentelemetry.javaagent.tooling.muzzle.references.Flag;
import io.opentelemetry.javaagent.tooling.muzzle.references.MethodRef;
import io.opentelemetry.javaagent.tooling.muzzle.references.Source;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Collections;
import org.objectweb.asm.Type;

/**
 * A mismatch between a {@link ClassRef} and a runtime class.
 *
 * <p>This class' {@link #toString()} returns a human-readable description of the mismatch along
 * with the first source code location of the reference which caused the mismatch.
 */
public abstract class Mismatch {
  /** Instrumentation sources which caused the mismatch. */
  private final Collection<Source> mismatchSources;

  private Mismatch(Collection<Source> mismatchSources) {
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

    public MissingClass(ClassRef classRef) {
      super(classRef.getSources());
      this.className = classRef.getClassName();
    }

    public MissingClass(ClassRef classRef, String className) {
      super(classRef.getSources());
      this.className = className;
    }

    @Override
    String getMismatchDetails() {
      return "Missing class " + className;
    }
  }

  public static class MissingFlag extends Mismatch {
    private final Flag expectedFlag;
    private final String classMethodOrFieldDesc;
    private final int foundAccess;

    public MissingFlag(
        Collection<Source> sources,
        String classMethodOrFieldDesc,
        Flag expectedFlag,
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

    MissingField(ClassRef classRef, FieldRef fieldRef) {
      super(fieldRef.getSources());
      this.className = classRef.getClassName();
      this.fieldName = fieldRef.getName();
      this.fieldDescriptor = fieldRef.getDescriptor();
    }

    MissingField(ClassRef classRef, HelperReferenceWrapper.Field field) {
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

    public MissingMethod(ClassRef classRef, MethodRef methodRef) {
      super(methodRef.getSources());
      this.className = classRef.getClassName();
      this.methodName = methodRef.getName();
      this.methodDescriptor = methodRef.getDescriptor();
    }

    public MissingMethod(ClassRef classRef, HelperReferenceWrapper.Method method) {
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
    private final ClassRef referenceBeingChecked;
    private final ClassLoader classLoaderBeingChecked;

    public ReferenceCheckError(
        Exception e, ClassRef referenceBeingChecked, ClassLoader classLoaderBeingChecked) {
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

  /**
   * Represents failure of some classloader to satisfy {@link
   * InstrumentationModule#classLoaderMatcher()}.
   */
  public static class InstrumentationModuleClassLoaderMismatch extends Mismatch {

    public InstrumentationModuleClassLoaderMismatch() {
      super(Collections.emptyList());
    }

    @Override
    String getMismatchDetails() {
      return "InstrumentationModule classloader check";
    }
  }

  /**
   * Represents failure to inject {@link InstrumentationModuleMuzzle#getMuzzleHelperClassNames()}
   * into some classloader.
   */
  public static class HelperClassesInjectionError extends Mismatch {

    public HelperClassesInjectionError() {
      super(Collections.emptyList());
    }

    @Override
    String getMismatchDetails() {
      return "Failed to inject helper classes. Are Helpers being injected in the correct order?";
    }
  }
}
