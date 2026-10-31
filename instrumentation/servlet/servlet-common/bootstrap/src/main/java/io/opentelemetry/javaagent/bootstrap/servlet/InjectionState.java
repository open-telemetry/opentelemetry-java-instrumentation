/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.servlet;

import java.util.function.BooleanSupplier;

// this is shared by both ServletOutputStream and PrintWriter injection
public final class InjectionState {
  private static final int HEAD_TAG_WRITTEN_FAKE_VALUE = -1;
  private static final int HEAD_TAG_PREFIX_LENGTH = "<head".length();
  private final BooleanSupplier currentRequest;
  private SnippetInjectingResponseWrapper wrapper;
  private int headTagBytesSeen = 0;
  private long contentLength = -1;
  private boolean writer;

  public InjectionState(SnippetInjectingResponseWrapper wrapper) {
    this(wrapper, () -> true);
  }

  public InjectionState(SnippetInjectingResponseWrapper wrapper, BooleanSupplier currentRequest) {
    this.wrapper = wrapper;
    this.currentRequest = currentRequest;
  }

  public void setWrapper(SnippetInjectingResponseWrapper wrapper) {
    this.wrapper = wrapper;
  }

  public void useWriter() {
    writer = true;
  }

  public boolean shouldProcessStreamWrite() {
    return !writer && shouldProcessWrite();
  }

  public void setContentLength(long contentLength) {
    this.contentLength = contentLength;
  }

  public long getContentLength() {
    return contentLength;
  }

  public boolean hasContentLength() {
    return contentLength != -1;
  }

  public void resetBuffer() {
    headTagBytesSeen = 0;
  }

  public void reset() {
    resetBuffer();
    contentLength = -1;
    writer = false;
  }

  public int getHeadTagBytesSeen() {
    return headTagBytesSeen;
  }

  public String getCharacterEncoding() {
    return wrapper.getCharacterEncoding();
  }

  public boolean isHeadTagWritten() {
    return headTagBytesSeen == HEAD_TAG_WRITTEN_FAKE_VALUE;
  }

  public boolean shouldProcessWrite() {
    if (!currentRequest.getAsBoolean() || isHeadTagWritten()) {
      return false;
    }
    if (!wrapper.isContentTypeTextHtml()) {
      // A partial head tag must not span writes of non-HTML content.
      headTagBytesSeen = 0;
      return false;
    }
    return true;
  }

  /**
   * Returns true when the byte is the last character of "<head>" and now is the right time to
   * inject. Otherwise, returns false.
   */
  public boolean processByte(int b) {
    if (isHeadTagWritten()) {
      return false;
    }
    if (inHeadTag(b)) {
      headTagBytesSeen++;
    } else {
      headTagBytesSeen = b == '<' ? 1 : 0;
    }
    if (headTagBytesSeen > HEAD_TAG_PREFIX_LENGTH && b == '>') {
      setHeadTagWritten();
      return true;
    } else {
      return false;
    }
  }

  private void setHeadTagWritten() {
    headTagBytesSeen = HEAD_TAG_WRITTEN_FAKE_VALUE;
  }

  private boolean inHeadTag(int b) {
    if (headTagBytesSeen == 0 && b == '<') {
      return true;
    } else if (headTagBytesSeen == 1 && b == 'h') {
      return true;
    } else if (headTagBytesSeen == 2 && b == 'e') {
      return true;
    } else if (headTagBytesSeen == 3 && b == 'a') {
      return true;
    } else if (headTagBytesSeen == 4 && b == 'd') {
      return true;
    } else if (headTagBytesSeen == 5 && (b == '>' || Character.isWhitespace(b))) {
      return true;
    } else {
      return headTagBytesSeen > 5;
    }
  }

  public SnippetInjectingResponseWrapper getWrapper() {
    return wrapper;
  }
}
