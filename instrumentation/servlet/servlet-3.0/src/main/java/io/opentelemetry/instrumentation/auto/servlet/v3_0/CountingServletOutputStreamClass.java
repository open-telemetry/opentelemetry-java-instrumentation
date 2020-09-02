package io.opentelemetry.instrumentation.auto.servlet.v3_0;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import javax.servlet.ServletOutputStream;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.modifier.FieldManifestation;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType.Unloaded;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.FieldValue;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;
import net.bytebuddy.matcher.ElementMatchers;

public class CountingServletOutputStreamClass {
  private static final String CLASS_NAME =
      "io.opentelemetry.instrumentation.auto.servlet.v3_0.CountingServletOutputStream";
  private static final Class<? extends ServletOutputStream> COUNTING_STREAM_CLASS;
  private static final MethodHandle CONSTRUCTOR;
  private static final MethodHandle GET_COUNTER;

  public static class ServletOutputStreamInterceptor {
    @RuntimeType
    public static void write(
        int b,
        @This ServletOutputStream countingOutputStream,
        @FieldValue("delegate") ServletOutputStream delegate,
        @FieldValue("counter") int counter)
        throws IOException {
      delegate.write(b);

      try {
        Field counterField = countingOutputStream.getClass().getField("counter");
        counterField.set(countingOutputStream, counter + 1);
      } catch (NoSuchFieldException | IllegalAccessException ignored) {
      }
    }

    @RuntimeType
    public static void write(
        byte[] b,
        @This ServletOutputStream countingOutputStream,
        @FieldValue("delegate") ServletOutputStream delegate,
        @FieldValue("counter") int counter)
        throws IOException {
      delegate.write(b);

      try {
        Field counterField = countingOutputStream.getClass().getField("counter");
        counterField.set(countingOutputStream, counter + b.length);
      } catch (NoSuchFieldException | IllegalAccessException ignored) {
      }
    }

    @RuntimeType
    public static void write(
        byte[] b,
        int off,
        int len,
        @This ServletOutputStream countingOutputStream,
        @FieldValue("delegate") ServletOutputStream delegate,
        @FieldValue("counter") int counter)
        throws IOException {
      delegate.write(b, off, len);

      try {
        Field counterField = countingOutputStream.getClass().getField("counter");
        counterField.set(countingOutputStream, counter + len);
      } catch (NoSuchFieldException | IllegalAccessException ignored) {
      }
    }
  }

  static {
    Class<? extends ServletOutputStream> countingStreamClass = null;
    try {
      // this has to be loaded together with helper classes
      Unloaded<ServletOutputStream> unloadedClass = new ByteBuddy()
          .subclass(ServletOutputStream.class)
          .name(CLASS_NAME)
          // define fields
          .defineField(
              "delegate",
              ServletOutputStream.class,
              Visibility.PRIVATE,
              FieldManifestation.FINAL)
          .defineField("counter", int.class, Visibility.PUBLIC)
          // define constructor
          .defineConstructor(Visibility.PUBLIC)
          .withParameters(ServletOutputStream.class)
          .intercept(
              MethodCall.invoke(ServletOutputStream.class.getDeclaredConstructor())
                  .onSuper()
                  .andThen(
                      FieldAccessor.ofField("delegate")
                          .setsArgumentAt(0)
                          .andThen(FieldAccessor.ofField("counter").setsDefaultValue())))
          // define counter accessor methods
          .defineMethod("getCounter", int.class, Visibility.PUBLIC)
          .intercept(FieldAccessor.ofField("counter"))
          .defineMethod("setCounter", void.class, Visibility.PUBLIC)
          .withParameters(int.class)
          .intercept(FieldAccessor.ofField("counter").setsArgumentAt(0))
          // pass-through methods
          .method(
              ElementMatchers.<MethodDescription>named("isReady")
                  .or(ElementMatchers.<MethodDescription>named("setWriteListener")))
          .intercept(MethodDelegation.toField("delegate"))
          .method(ElementMatchers.<MethodDescription>named("flush"))
          .intercept(
              MethodCall.invoke(ServletOutputStream.class.getMethod("flush"))
                  .onField("delegate"))
          .method(ElementMatchers.<MethodDescription>named("close"))
          .intercept(
              MethodCall.invoke(ServletOutputStream.class.getMethod("close"))
                  .onField("delegate"))
          // counting methods
          .method(ElementMatchers.<MethodDescription>named("write"))
          .intercept(MethodDelegation.to(ServletOutputStreamInterceptor.class))
          .make();

      System.out.println(unloadedClass.getAllTypes().keySet());

      countingStreamClass =
          unloadedClass
              .load(CountingServletOutputStreamClass.class.getClassLoader())
              .getLoaded();
    } catch (NoSuchMethodException ignored) {
    }
    COUNTING_STREAM_CLASS = countingStreamClass;

    MethodHandle constructor = null;
    MethodHandle getCounter = null;
    if (COUNTING_STREAM_CLASS != null) {
      MethodHandles.Lookup lookup = MethodHandles.publicLookup();

      try {
        constructor =
            lookup.findConstructor(
                COUNTING_STREAM_CLASS,
                MethodType.methodType(void.class, ServletOutputStream.class));
      } catch (NoSuchMethodException | IllegalAccessException ignored) {
      }

      try {
        getCounter = lookup.findGetter(COUNTING_STREAM_CLASS, "counter", int.class);
      } catch (IllegalAccessException | NoSuchFieldException ignored) {
      }
    }
    CONSTRUCTOR = constructor;
    GET_COUNTER = getCounter;
  }

  public static ServletOutputStream wrap(ServletOutputStream delegate) {
    if (CONSTRUCTOR == null) {
      return delegate;
    }
    try {
      return (ServletOutputStream) CONSTRUCTOR.invoke(delegate);
    } catch (Error | RuntimeException e) {
      throw e;
    } catch (Throwable ignored) {
      return delegate;
    }
  }

  public static int getCounter(ServletOutputStream countingStream) {
    if (GET_COUNTER == null || countingStream.getClass() != COUNTING_STREAM_CLASS) {
      return 0;
    }
    try {
      return (int) GET_COUNTER.invoke(countingStream);
    } catch (Error | RuntimeException e) {
      throw e;
    } catch (Throwable ignored) {
      return 0;
    }
  }
}
