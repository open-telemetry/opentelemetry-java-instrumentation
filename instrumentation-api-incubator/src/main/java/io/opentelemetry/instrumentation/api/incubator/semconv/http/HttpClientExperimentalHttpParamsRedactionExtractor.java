/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import io.opentelemetry.semconv.UrlAttributes;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;

public class HttpClientExperimentalHttpParamsRedactionExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  private final HttpClientAttributesGetter<REQUEST, RESPONSE> attributesGetter;

  private static final Set<String> PARAMS_TO_REDACT =
      new HashSet<>(Arrays.asList("AWSAccessKeyId", "Signature", "sig", "X-Goog-Signature"));

  public HttpClientExperimentalHttpParamsRedactionExtractor(
      HttpClientAttributesGetter<REQUEST, RESPONSE> attributesGetter) {
    this.attributesGetter = attributesGetter;
  }

  public static <REQUEST, RESPONSE>
      HttpClientExperimentalHttpParamsRedactionExtractor<REQUEST, RESPONSE> create(
          HttpClientAttributesGetter<REQUEST, RESPONSE> attributesGetter) {
    return new HttpClientExperimentalHttpParamsRedactionExtractor<>(attributesGetter);
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    String urlFull = attributesGetter.getUrlFull(request);
    String redactedUrlParameters = redactUrlParameters(urlFull);
    internalSet(attributes, UrlAttributes.URL_FULL, redactedUrlParameters);
  }

  private static String redactUrlParameters(String url) {
    int questionMarkIndex = url.indexOf('?');
    if (questionMarkIndex == -1 || !containsParamToRedact(url)) {
      return url;
    }

    boolean paramToRedact = false; // To be able to skip the characters of the parameters to redact
    boolean paramNameDetected = false;
    boolean reference = false;

    StringBuilder urlPartAfterQuestionMark = new StringBuilder();

    // To build a parameter name until we reach the '=' character
    // If the parameter name is a one to redact, we will redact the value
    StringBuilder currentParamName = new StringBuilder();

    for (int i = questionMarkIndex + 1; i < url.length(); i++) {
      char currentChar = url.charAt(i);
      if (currentChar == '=') {
        paramNameDetected = true;
        urlPartAfterQuestionMark.append(currentParamName);
        urlPartAfterQuestionMark.append('=');
        if (PARAMS_TO_REDACT.contains(currentParamName.toString())) {
          urlPartAfterQuestionMark.append("REDACTED");
          paramToRedact = true;
        }
      } else if (currentChar == '&') { // New parameter delimiter
        urlPartAfterQuestionMark.append('&');
        paramNameDetected = false;
        paramToRedact = false;
        currentParamName.setLength(
            0); // To avoid creating a new StringBuilder for each new parameter
      } else if (currentChar == '#') { // Reference delimiter
        reference = true;
        urlPartAfterQuestionMark.append('#');
      } else if (!paramNameDetected) {
        currentParamName.append(currentChar);
      } else if (!paramToRedact || reference) {
        urlPartAfterQuestionMark.append(currentChar);
      }
    }
    return url.substring(0, questionMarkIndex) + "?" + urlPartAfterQuestionMark;
  }

  private static boolean containsParamToRedact(String urlpart) {
    for (String param : PARAMS_TO_REDACT) {
      if (urlpart.contains(param)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {}
}
