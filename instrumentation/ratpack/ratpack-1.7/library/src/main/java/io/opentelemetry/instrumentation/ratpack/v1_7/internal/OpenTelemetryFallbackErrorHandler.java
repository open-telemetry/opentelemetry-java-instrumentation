/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.ratpack.v1_7.internal;

import static java.util.logging.Level.WARNING;

import java.util.logging.Logger;
import ratpack.error.ClientErrorHandler;
import ratpack.error.ServerErrorHandler;
import ratpack.handling.Context;

// Copied from
// https://github.com/ratpack/ratpack/blob/master/ratpack-core/src/main/java/ratpack/core/error/internal/DefaultProductionErrorHandler.java
// since it is internal and has had breaking changes.
/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
final class OpenTelemetryFallbackErrorHandler implements ClientErrorHandler, ServerErrorHandler {

  static final OpenTelemetryFallbackErrorHandler INSTANCE = new OpenTelemetryFallbackErrorHandler();

  private static final Logger logger =
      Logger.getLogger(OpenTelemetryFallbackErrorHandler.class.getName());

  OpenTelemetryFallbackErrorHandler() {}

  @Override
  public void error(Context context, int statusCode) {
    if (logger.isLoggable(WARNING)) {
      WarnOnce.execute();
      logger.warning(getMsg(ClientErrorHandler.class, "client error", context));
    }
    context.getResponse().status(statusCode).send();
  }

  @Override
  public void error(Context context, Throwable throwable) {
    if (logger.isLoggable(WARNING)) {
      WarnOnce.execute();
      logger.log(
          WARNING, getMsg(ServerErrorHandler.class, "server error", context) + "\n", throwable);
    }
    context.getResponse().status(500).send();
  }

  private static String getMsg(Class<?> handlerClass, String type, Context context) {
    return "Default production error handler used to render "
        + type
        + ", please add a "
        + handlerClass.getName()
        + " instance to your application "
        + "(method: "
        + context.getRequest().getMethod()
        + ", uri: "
        + context.getRequest().getRawUri()
        + ")";
  }

  private static class WarnOnce {
    static {
      logger.warning(
          "Logging error using OpenTelemetryFallbackErrorHandler. This indicates "
              + "OpenTelemetry could not find a registered error handler which is not expected. "
              + "Log messages will only be outputted to console.");
    }

    // Warned once in static initializer, this is just to trigger classload.
    static void execute() {}
  }
}
