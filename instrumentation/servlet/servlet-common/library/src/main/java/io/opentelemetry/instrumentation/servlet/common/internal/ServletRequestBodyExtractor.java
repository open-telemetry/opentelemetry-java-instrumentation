/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.common.internal;

import static io.opentelemetry.instrumentation.servlet.common.internal.BodyCaptureHelper.REQUEST_BODY_BUFFER_ATTRIBUTE;
import static io.opentelemetry.instrumentation.servlet.common.internal.BodyCaptureHelper.REQUEST_BODY_MARKER_ATTRIBUTE;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
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
      ServletRequestContext<REQUEST> requestServletRequestContext) {

    REQUEST request = requestServletRequestContext.request();
    String contentType = accessor.getRequestHeader(request, "Content-Type");
    String contentEncoding = accessor.getRequestHeader(request, "Content-Encoding");
    if (!shouldMarkRequestForBodyCapture(config, contentType, contentEncoding)) {
      return;
    }
    accessor.setRequestAttribute(request, REQUEST_BODY_MARKER_ATTRIBUTE, true);
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ServletRequestContext<REQUEST> requestServletRequestContext,
      @Nullable ServletResponseContext<RESPONSE> responseServletResponseContext,
      @Nullable Throwable error) {

    REQUEST request = requestServletRequestContext.request();

    String body = extractRequestBody(accessor, request);
    if (body != null) {
      attributes.put(SPAN_BODY_ATTRIBUTE, body);
    }
  }

  /**
   * Determines whether the request should be marked for body capture.
   *
   * @param config configuration
   * @param contentType value of the content-type header, if available
   * @param contentEncoding value of the content-encoding header, if available
   * @return true if the request should be marked for body capture, false otherwise
   */
  public static boolean shouldMarkRequestForBodyCapture(
      BodyCaptureConfig config, @Nullable String contentType, @Nullable String contentEncoding) {
    if (!config.isEnabled()) {
      return false;
    }
    // prevent capturing the body when content-encoding indicates a binary body
    if (contentEncoding != null && !"identity".equalsIgnoreCase(contentEncoding)) {
      return false;
    }

    if (contentType == null) {
      // no body capture without content type
      return false;
    }

    String mediaType = getAndNormalizeMediaTypeFromContentTypeHeader(contentType);
    if (null == mediaType) {
      // don't attempt to decode body without media type
      return false;
    }
    if (mediaType.startsWith("multipart/")) {
      // multipart bodies can contain binary data: never captured, whatever the configuration
      return false;
    }
    return config.getMediaTypes().matches(mediaType);
  }

  /**
   * Extract and removes the request body and marker stored in request attributes.
   *
   * @param <REQUEST> request type
   * @param <RESPONSE> response type
   * @param accessor servlet accessor
   * @param request servlet request
   * @return extracted request body value, or {@code null} if no or empty request body was captured
   */
  @Nullable
  public static <REQUEST, RESPONSE> String extractRequestBody(
      ServletAccessor<REQUEST, RESPONSE> accessor, REQUEST request) {

    if (consumeRequestAttribute(accessor, request, REQUEST_BODY_MARKER_ATTRIBUTE) == null) {
      // only requests with marker should be processed
      return null;
    }

    Object bodyAttribute =
        consumeRequestAttribute(accessor, request, REQUEST_BODY_BUFFER_ATTRIBUTE);
    if (!(bodyAttribute instanceof ByteBuffer)) {
      return null;
    }

    String contentType = accessor.getRequestHeader(request, "Content-Type");
    return getRequestBodyValue((ByteBuffer) bodyAttribute, contentType);
  }

  private static <REQUEST, RESPONSE> Object consumeRequestAttribute(
      ServletAccessor<REQUEST, RESPONSE> accessor, REQUEST request, String key) {
    Object value = accessor.getRequestAttribute(request, key);
    if (value != null) {
      accessor.setRequestAttribute(request, key, null);
    }
    return value;
  }

  /**
   * Extracts request body value, body capture pre-requisites must have been checked with {@link
   * #shouldMarkRequestForBodyCapture(BodyCaptureConfig, String, String)} before calling this
   * method.
   *
   * @param buffer request body buffer
   * @param contentType value of the Content-Type header, which may contain a charset parameter
   * @return extracted body value as string, or {@code null} if the body is empty, or the content
   *     type is not supported, unknown, missing or excluded by configuration.
   */
  @Nullable
  public static String getRequestBodyValue(ByteBuffer buffer, @Nullable String contentType) {
    if (buffer.position() == 0 || contentType == null) {
      // empty buffer or no content type header provided
      return null;
    }

    buffer.flip();
    CharsetDecoder decoder =
        getCharsetFromContentTypeHeader(contentType)
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
    // a byte never decodes to more than one char, so this is always large enough
    CharBuffer text = CharBuffer.allocate(buffer.remaining());
    // On the first invalid byte sequence, which includes a multi-byte character cut by the size
    // limit, decoding stops: we keep the text decoded so far and never add replacement characters.
    decoder.decode(buffer, text, true);
    text.flip();
    return text.toString();
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
