/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import java.util.Optional;
import javax.annotation.Nullable;
import org.apache.pekko.http.IllegalRequestContext;
import org.apache.pekko.http.javadsl.model.HttpMethod;
import org.apache.pekko.http.javadsl.model.HttpProtocol;
import org.apache.pekko.http.scaladsl.model.ErrorInfo;

/**
 * Reads the rejected request out of the {@code IllegalRequestContext} that Pekko HTTP 2.0 added.
 * Kept apart from {@link PekkoHttpParsingErrorSingletons} so that only the Pekko HTTP 2.0
 * instrumentation references the class.
 */
public class PekkoHttpIllegalRequestContextUtil {

  @Nullable
  public static Object[] startSpan(ErrorInfo info, IllegalRequestContext requestContext) {
    Optional<HttpMethod> method = requestContext.getMethod();
    Optional<HttpProtocol> protocol = requestContext.getProtocol();
    return PekkoHttpParsingErrorSingletons.startSpan(
        info,
        method.isPresent() ? method.get().value() : null,
        requestContext.getRawRequestTarget().orElse(null),
        protocol.isPresent() ? protocol.get().value() : null);
  }

  private PekkoHttpIllegalRequestContextUtil() {}
}
