/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import com.amazonaws.services.lambda.runtime.Context;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Model for wrapped lambda function (object, class, method). */
class WrappedLambda {

  public static final String OTEL_LAMBDA_HANDLER_ENV_KEY =
      "OTEL_INSTRUMENTATION_AWS_LAMBDA_HANDLER";

  private final Object targetObject;
  private final Class<?> targetClass;
  private final String targetMethodName;

  /**
   * Creates new lambda wrapper out of configuration. Supported env properties: - {@value
   * OTEL_LAMBDA_HANDLER_ENV_KEY} - lambda handler in format: package.ClassName::methodName
   */
  static WrappedLambda fromConfiguration() {

    String lambdaHandler = System.getenv(OTEL_LAMBDA_HANDLER_ENV_KEY);
    if (lambdaHandler == null || lambdaHandler.isEmpty()) {
      throw new IllegalStateException(OTEL_LAMBDA_HANDLER_ENV_KEY + " was not specified.");
    }
    // expect format to be package.ClassName::methodName
    String[] split = lambdaHandler.split("::");
    if (split.length != 2) {
      throw new IllegalStateException(
          lambdaHandler
              + " is not a valid handler name. Expected format: package.ClassName::methodName");
    }
    String handlerClassName = split[0];
    String targetMethodName = split[1];
    Class<?> targetClass;
    try {
      targetClass = Class.forName(handlerClassName);
    } catch (ClassNotFoundException e) {
      // no class found
      throw new IllegalStateException(handlerClassName + " not found in classpath", e);
    }
    return new WrappedLambda(targetClass, targetMethodName);
  }

  WrappedLambda(Class<?> targetClass, String targetMethodName) {
    this.targetClass = targetClass;
    this.targetMethodName = targetMethodName;
    this.targetObject = instantiateTargetClass();
  }

  private Object instantiateTargetClass() {
    Object targetObject;
    try {
      Constructor<?> ctor = targetClass.getConstructor();
      targetObject = ctor.newInstance();
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(
          targetClass.getName() + " does not have an appropriate constructor", e);
    } catch (InstantiationException e) {
      throw new IllegalStateException(targetClass.getName() + " cannot be an abstract class", e);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(
          targetClass.getName() + "'s constructor is not accessible", e);
    } catch (InvocationTargetException e) {
      throw new IllegalStateException(
          targetClass.getName() + " threw an exception from the constructor", e);
    }
    return targetObject;
  }

  private static boolean isLastParameterContext(Parameter[] parameters) {
    if (parameters.length == 0) {
      return false;
    }
    return parameters[parameters.length - 1].getType().equals(Context.class);
  }

  Method getRequestTargetMethod() {

    List<Method> methods = Arrays.asList(targetClass.getMethods());
    Optional<Method> firstOptional =
        methods.stream()
            .filter((Method m) -> m.getName().equals(targetMethodName))
            .min(WrappedLambda::methodComparator);
    if (!firstOptional.isPresent()) {
      throw new IllegalStateException("Method " + targetMethodName + " not found");
    }
    return firstOptional.get();
  }

  /*
   Per method selection specifications
   http://docs.aws.amazon.com/lambda/latest/dg/java-programming-model-handler-types.html
   - Context can be omitted
   - Select the method with the largest number of parameters.
   - If two or more methods have the same number of parameters, AWS Lambda selects the method that has the Context as the last parameter.
   - Non-Bridge methods are preferred
   - If none or all of these methods have the Context parameter, then the behavior is undefined.

   Examples:
   - handleA(String, String, Integer), handleB(String, Context) - handleA is selected (number of parameters)
   - handleA(String, String, Integer), handleB(String, String, Context) - handleB is selected (has Context as the last parameter)
   - generic method handleG(T, U, Context), implementation (T, U - String) handleA(String, String, Context), bridge method handleB(Object, Object, Context) - handleA is selected (non-bridge)
  */
  private static int methodComparator(Method a, Method b) {
    // greater number of params wins
    if (a.getParameterCount() != b.getParameterCount()) {
      return b.getParameterCount() - a.getParameterCount();
    }
    // only one of the methods has last param context ?
    int onlyOneHasCtx = onlyOneHasContextAsLastParam(a, b);
    if (onlyOneHasCtx != 0) {
      return onlyOneHasCtx;
    }
    // one of the methods is a bridge, otherwise - undefined
    return onlyOneIsBridgeMethod(a, b);
  }

  private static int onlyOneIsBridgeMethod(Method first, Method second) {
    boolean firstBridge = first.isBridge();
    boolean secondBridge = second.isBridge();
    if (firstBridge && !secondBridge) {
      return 1;
    } else if (!firstBridge && secondBridge) {
      return -1;
    }
    return 0;
  }

  private static int onlyOneHasContextAsLastParam(Method first, Method second) {
    boolean firstCtx = isLastParameterContext(first.getParameters());
    boolean secondCtx = isLastParameterContext(second.getParameters());
    // only one of the methods has last param context ?
    if (firstCtx && !secondCtx) {
      return -1;
    } else if (!firstCtx && secondCtx) {
      return 1;
    }
    return 0;
  }

  Object getTargetObject() {
    return targetObject;
  }

  Class<?> getTargetClass() {
    return targetClass;
  }
}
