/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.common.internal;

import static io.opentelemetry.instrumentation.servlet.common.internal.BodyCaptureHelper.REQUEST_BODY_ATTRIBUTE;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
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
  // TODO: replace this when available in semconv 1.45.0
  public static final AttributeKey<Value<?>> SPAN_BODY_ATTRIBUTE =
      AttributeKey.valueKey("http.request.body.content");

  private static final Pattern CHARSET_PATTERN =
      Pattern.compile("charset\\s*=\\s*\"?([^\\s\";]+)", Pattern.CASE_INSENSITIVE);

  private final ServletAccessor<REQUEST, RESPONSE> accessor;

  public ServletRequestBodyExtractor(ServletAccessor<REQUEST, RESPONSE> accessor) {
    this.accessor = accessor;
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

    Value<?> body = extractRequestBody(accessor, requestServletRequestContext.request());
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
  public static <REQUEST, RESPONSE> Value<?> extractRequestBody(
      ServletAccessor<REQUEST, RESPONSE> accessor, REQUEST request) {
    Object bodyAttribute = accessor.getRequestAttribute(request, REQUEST_BODY_ATTRIBUTE);
    if (!(bodyAttribute instanceof ByteBuffer)) {
      return null;
    }

    // remove request attribute so the buffer is no longer accessible for writing
    accessor.setRequestAttribute(request, REQUEST_BODY_ATTRIBUTE, null);

    String contentType = accessor.getRequestHeader(request, "Content-Type");
    return getRequestBodyValue((ByteBuffer) bodyAttribute, contentType);
  }

  @Nullable
  public static Value<?> getRequestBodyValue(ByteBuffer buffer, @Nullable String contentType) {
    if (buffer.position() == 0) {
      // empty buffer
      return null;
    }

    buffer.flip();
    ByteBuffer original = buffer.duplicate();
    try {
      // malformed input must be reported, not silently replaced, so that a body that is not valid
      // text in the expected charset is captured as binary
      CharsetDecoder decoder =
          getCharset(contentType)
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT);
      // decode a duplicate to keep the buffer content available for the binary fallback
      return Value.of(decoder.decode(buffer.duplicate()).toString());
    } catch (CharacterCodingException e) {
      // TODO: create a dedicated Value<ByteBuffer> implementation that avoids doing any copy
      // we can do that because we know the buffer will not be modified anymore.
      original.arrayOffset(); // ensure array is accessible
      return Value.of(original.array());
    }
  }

  /** Returns the charset from the content type, or UTF-8 when it is missing or unknown. */
  private static Charset getCharset(@Nullable String contentType) {
    if (contentType == null) {
      return UTF_8;
    }
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
