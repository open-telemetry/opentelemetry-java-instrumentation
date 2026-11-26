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
   * Extract and removes the request body and marker stored in request attributes. When no body has
   * been captured yet, the request attributes are left untouched so the body can be extracted
   * later.
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

    if (accessor.getRequestAttribute(request, REQUEST_BODY_MARKER_ATTRIBUTE) == null) {
      // only requests with marker should be processed
      return null;
    }

    Object bodyAttribute = accessor.getRequestAttribute(request, REQUEST_BODY_BUFFER_ATTRIBUTE);
    if (!(bodyAttribute instanceof ByteBuffer) || ((ByteBuffer) bodyAttribute).position() == 0) {
      // Nothing captured yet: the body might be read later, for example after the servlet
      // service() method has returned, so the marker and buffer must be kept for a later call.
      return null;
    }

    String contentType = accessor.getRequestHeader(request, "Content-Type");
    String bodyValue = getRequestBodyValue((ByteBuffer) bodyAttribute, contentType);

    // remove the attributes so the buffer is no longer accessible for writing and the body is
    // only extracted once after successful capture.
    accessor.setRequestAttribute(request, REQUEST_BODY_MARKER_ATTRIBUTE, null);
    accessor.setRequestAttribute(request, REQUEST_BODY_BUFFER_ATTRIBUTE, null);

    return bodyValue;
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

  /**
   * Returns the charset from the content type, or UTF-8 when it is missing or unknown.
   *
   * <p>Parameters are separated by {@code ;}, their names are case-insensitive and their values can
   * be quoted, in which case a {@code ;} is part of the value (RFC 9110, section 5.6.6). A
   * parameter can't be specified more than once (RFC 6838, section 4.3), so the first {@code
   * charset} parameter is used.
   */
  // package-protected for testing
  static Charset getCharsetFromContentTypeHeader(String contentType) {
    int length = contentType.length();
    int start = contentType.indexOf(';');
    while (start != -1 && start < length) {
      // find the end of the parameter, ignoring any ';' in a quoted value
      int end = start + 1;
      boolean quoted = false;
      while (end < length && (quoted || contentType.charAt(end) != ';')) {
        char c = contentType.charAt(end);
        if (c == '"') {
          quoted = !quoted;
        } else if (c == '\\' && quoted) {
          end++; // skip the escaped character
        }
        end++;
      }

      String parameter = contentType.substring(start + 1, Math.min(end, length));
      int separator = parameter.indexOf('=');
      if (separator != -1 && "charset".equalsIgnoreCase(parameter.substring(0, separator).trim())) {
        String name = parameter.substring(separator + 1).trim();
        if (name.length() >= 2 && name.startsWith("\"") && name.endsWith("\"")) {
          name = name.substring(1, name.length() - 1);
        }
        try {
          return Charset.forName(name);
        } catch (IllegalArgumentException e) {
          // illegal or unsupported charset name, use the default
          return UTF_8;
        }
      }
      start = end;
    }
    return UTF_8;
  }
}
