/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ImplicitContextKeyed;
import io.opentelemetry.context.Scope;
import javax.annotation.Nullable;

/** A helper class for setting {@code url.template} attribute value for HTTP client calls. */
public final class HttpClientUrlTemplate {

  /**
   * Add url template to context and make the new context current. Http client calls made while the
   * context is active will set {@code url.template} attribute to the supplied value.
   */
  public static Scope with(Context context, String urlTemplate) {
    return context.with(new UrlTemplateState(urlTemplate)).makeCurrent();
  }

  @Nullable
  public static String get(Context context) {
    UrlTemplateState state = UrlTemplateState.fromContextOrNull(context);
    return state != null ? state.urlTemplate : null;
  }

  private HttpClientUrlTemplate() {}

  private static class UrlTemplateState implements ImplicitContextKeyed {

    private static final ContextKey<UrlTemplateState> KEY =
        ContextKey.named("opentelemetry-http-client-url-template-key");

    private final String urlTemplate;

    @Nullable
    static UrlTemplateState fromContextOrNull(Context context) {
      return context.get(KEY);
    }

    UrlTemplateState(String urlTemplate) {
      this.urlTemplate = urlTemplate;
    }

    @Override
    public Context storeInContext(Context context) {
      return context.with(KEY, this);
    }
  }
}
