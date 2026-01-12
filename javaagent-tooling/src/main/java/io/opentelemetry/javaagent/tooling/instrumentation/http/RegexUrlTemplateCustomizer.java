/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.http;

import static io.opentelemetry.javaagent.tooling.instrumentation.http.UrlTemplateRules.getRules;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpClientUrlTemplateCustomizer;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import io.opentelemetry.javaagent.tooling.instrumentation.http.UrlTemplateRules.Rule;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

@AutoService(HttpClientUrlTemplateCustomizer.class)
public final class RegexUrlTemplateCustomizer implements HttpClientUrlTemplateCustomizer {

  @Override
  @Nullable
  public <REQUEST> String customize(
      @Nullable String urlTemplate,
      REQUEST request,
      HttpClientAttributesGetter<REQUEST, ?> getter) {
    String url = getter.getUrlFull(request);
    if (url == null) {
      return null;
    }

    for (Rule rule : getRules()) {
      if (urlTemplate != null && !rule.getOverride()) {
        continue;
      }

      Pattern pattern = rule.getPattern();
      // to generate the url template, we apply the regex replacement on the full url
      String result = pattern.matcher(url).replaceFirst(rule.getReplacement());
      if (!url.equals(result)) {
        return result;
      }
    }

    return null;
  }
}
