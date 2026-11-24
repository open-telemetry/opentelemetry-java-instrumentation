/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.common.internal;

import static io.opentelemetry.instrumentation.servlet.common.internal.BodyCaptureHelper.REQUEST_BODY_ATTRIBUTE;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ServletRequestBodyExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<
        ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> {

  /** Semconv attribute used to store request body in span */
  // TODO: replace this when available in semconv
  public static final AttributeKey<String> SPAN_BODY_ATTRIBUTE =
      AttributeKey.stringKey("http.request.body.content");

  private static final Pattern CHARSET_PATTERN =
      Pattern.compile("charset\\s*=\\s*\"?([^\\s\";]+)", Pattern.CASE_INSENSITIVE);

  private final ServletAccessor<REQUEST, RESPONSE> accessor;
  private final BodyCaptureConfig config;

  public ServletRequestBodyExtractor(
      ServletAccessor<REQUEST, RESPONSE> accessor, BodyCaptureConfig config) {
    this.accessor = accessor;
    this.config = config;
  }

  @Override
  public void onStart(
      AttributesBuilder attributes,
      Context parentContext,
      ServletRequestContext<REQUEST> requestServletRequestContext) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ServletRequestContext<REQUEST> requestServletRequestContext,
      @Nullable ServletResponseContext<RESPONSE> responseServletResponseContext,
      @Nullable Throwable error) {

    if (!config.isEnabled()) {
      return;
    }

    String body = extractRequestBody(accessor, requestServletRequestContext.request(), config);
    if (body != null) {
      attributes.put(SPAN_BODY_ATTRIBUTE, body);
    }
  }

  /**
   * Extract and removes the request body stored in request attributes.
   *
   * @param accessor servlet accessor
   * @param request servlet request
   * @param <REQUEST> request type
   * @param <RESPONSE> response type
   * @return extracted request body value, or {@code null} if no or empty request body was captured
   */
  @Nullable
  public static <REQUEST, RESPONSE> String extractRequestBody(
      ServletAccessor<REQUEST, RESPONSE> accessor, REQUEST request, BodyCaptureConfig config) {
    Object bodyAttribute = accessor.getRequestAttribute(request, REQUEST_BODY_ATTRIBUTE);
    if (!(bodyAttribute instanceof ByteBuffer)) {
      return null;
    }
    String contentType = accessor.getRequestHeader(request, "Content-Type");

    // remove request attribute so the buffer is no longer accessible for writing
    accessor.setRequestAttribute(request, REQUEST_BODY_ATTRIBUTE, null);
    return getRequestBodyValue((ByteBuffer) bodyAttribute, contentType, config);
  }

  /**
   * Extracts request body value
   *
   * @param buffer request body buffer
   * @param contentType value of the Content-Type header, which may contain a charset parameter
   * @param config request body capture configuration
   * @return extracted body value as string, or {@code null} if the body is empty, or the content
   *     type is not supported, unknown, missing or excluded by configuration.
   */
  @Nullable
  public static String getRequestBodyValue(
      ByteBuffer buffer, @Nullable String contentType, BodyCaptureConfig config) {
    if (buffer.position() == 0 || contentType == null) {
      // empty buffer or no content type header provided
      return null;
    }

    String mediaType = getAndNormalizeMediaTypeFromContentTypeHeader(contentType);
    if (null == mediaType) {
      // don't attempt to decode body without media type
      return null;
    }

    if (!config.getMediaTypes().matches(mediaType)) {
      // media type is not allowed by configuration
      return null;
    }

    buffer.flip();
    try {
      CharsetDecoder decoder =
          getCharsetFromContentTypeHeader(contentType)
              .newDecoder()
              // ignore allows to decode as much as we can and cut of the rest.
              .onMalformedInput(CodingErrorAction.IGNORE)
              .onUnmappableCharacter(CodingErrorAction.IGNORE);
      return decoder.decode(buffer).toString();
    } catch (CharacterCodingException e) {
      return null;
    }
  }

  // package-protected for testing
  @Nullable
  static String getAndNormalizeMediaTypeFromContentTypeHeader(String contentType) {
    if (contentType == null || contentType.isEmpty()) {
      return null;
    }
    contentType = contentType.toLowerCase(Locale.ROOT);
    int semicolonIndex = contentType.indexOf(';');
    String mediaType = contentType;
    if (semicolonIndex != -1) {
      mediaType = contentType.substring(0, semicolonIndex);
    }
    mediaType = mediaType.trim();
    return mediaType.isEmpty() ? null : mediaType;
  }

  /** Returns the charset from the content type, or UTF-8 when it is missing or unknown. */
  // package-protected for testing
  static Charset getCharsetFromContentTypeHeader(String contentType) {
    // TODO: we should be able to optimize this
    // - cache (bounded) for charset resolution from content type header
    // - parser charset without regex

    Matcher matcher = CHARSET_PATTERN.matcher(contentType);
    if (matcher.find()) {
      try {
        return Charset.forName(matcher.group(1));
      } catch (IllegalArgumentException e) {
        // illegal or unsupported charset name, use the default
      }
    }
    return UTF_8;
  }
}
