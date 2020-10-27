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

  public static final String OTEL_LAMBDA_HANDLER_ENV_KEY = "OTEL_LAMBDA_HANDLER";

  private final Object targetObject;
  private final Class<?> targetClass;
  private final String targetMethodName;

  /**
   * Creates new lambda wrapper out of configuration. Supported env properties: - {@value
   * OTEL_LAMBDA_HANDLER_ENV_KEY} - lambda handler in format: package.ClassName::methodName
   *
   * @return
   */
  static WrappedLambda fromConfiguration() {

    String lambdaHandler = System.getenv(OTEL_LAMBDA_HANDLER_ENV_KEY);
    if (lambdaHandler == null || lambdaHandler.isEmpty()) {
      throw new RuntimeException(OTEL_LAMBDA_HANDLER_ENV_KEY + " was not specified.");
    }
    // expect format to be package.ClassName::methodName
    String[] split = lambdaHandler.split("::");
    if (split.length != 2) {
      throw new RuntimeException(
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
      throw new RuntimeException(handlerClassName + " not found in classpath");
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
      throw new RuntimeException(
          targetClass.getName() + " does not have an appropriate constructor");
    } catch (InstantiationException e) {
      throw new RuntimeException(targetClass.getName() + " cannot be an abstract class");
    } catch (IllegalAccessException e) {
      throw new RuntimeException(targetClass.getName() + "'s constructor is not accessible");
    } catch (InvocationTargetException e) {
      throw new RuntimeException(
          targetClass.getName() + " threw an exception from the constructor");
    }
    return targetObject;
  }

  private boolean isLastParameterContext(Parameter[] parameters) {
    if (parameters.length == 0) {
      return false;
    }
    return parameters[parameters.length - 1].getType().equals(Context.class);
  }

  Method getRequestTargetMethod() {
    /*
       Per method selection specifications
       http://docs.aws.amazon.com/lambda/latest/dg/java-programming-model-handler-types.html
       - Context can be omitted
       - Select the method with the largest number of parameters.
       - If two or more methods have the same number of parameters, AWS Lambda selects the method that has the Context as the last parameter.
       - If none or all of these methods have the Context parameter, then the behavior is undefined.
    */
    List<Method> methods = Arrays.asList(targetClass.getMethods());
    Optional<Method> firstOptional =
        methods.stream()
            .filter((Method m) -> m.getName().equals(targetMethodName))
            .sorted(
                (Method a, Method b) -> {
                  // sort descending (reverse of default ascending)
                  if (a.getParameterCount() != b.getParameterCount()) {
                    return b.getParameterCount() - a.getParameterCount();
                  }
                  if (isLastParameterContext(a.getParameters())) {
                    return -1;
                  } else if (isLastParameterContext(b.getParameters())) {
                    return 1;
                  }
                  return -1;
                })
            .findFirst();
    if (!firstOptional.isPresent()) {
      throw new RuntimeException("Method " + targetMethodName + " not found");
    }
    return firstOptional.get();
  }

  Object getTargetObject() {
    return targetObject;
  }

  Class<?> getTargetClass() {
    return targetClass;
  }
}
