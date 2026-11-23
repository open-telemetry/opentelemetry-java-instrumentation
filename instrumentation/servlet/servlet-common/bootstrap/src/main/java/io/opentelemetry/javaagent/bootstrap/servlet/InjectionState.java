/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.servlet;

// Shared by response wrappers for one response body, including async redispatch.
// The stream's virtual field can outlive the request when the container recycles the stream.
public final class InjectionState {
  private static final int HEAD_TAG_WRITTEN_FAKE_VALUE = -1;
  private static final int HEAD_TAG_PREFIX_LENGTH = "<head".length();
  private SnippetInjectingResponseWrapper wrapper;
  private SnippetInjectingResponseWrapper contentLengthWrapper;
  private int headTagBytesSeen = 0;
  private long contentLength = -1;
  private boolean contentLengthAdjusted;
  private boolean writer;
  private boolean forwardPending;
  private long resetSequence;

  public InjectionState(SnippetInjectingResponseWrapper wrapper) {
    this.wrapper = wrapper;
    contentLengthWrapper = wrapper;
  }

  public void setWrapper(SnippetInjectingResponseWrapper wrapper) {
    this.wrapper = wrapper;
  }

  public void setContentLengthWrapper(SnippetInjectingResponseWrapper wrapper) {
    contentLengthWrapper = wrapper;
  }

  public void useWriter() {
    writer = true;
  }

  public void beginForward() {
    forwardPending = true;
  }

  public boolean shouldProcessStreamWrite() {
    return !writer && shouldProcessWrite();
  }

  public void setContentLength(long contentLength) {
    this.contentLength = contentLength;
    contentLengthAdjusted = false;
  }

  public void markContentLengthAdjusted() {
    contentLengthAdjusted = true;
  }

  public boolean isContentLengthAdjusted() {
    return contentLengthAdjusted;
  }

  public long getContentLength() {
    return contentLength;
  }

  public boolean hasContentLength() {
    return contentLength != -1;
  }

  public void resetBuffer() {
    contentLengthWrapper.resetContentLengthIfPreviouslyAdjusted();
    headTagBytesSeen = 0;
    if (forwardPending) {
      forwardPending = false;
      writer = false;
    }
    resetSequence++;
  }

  public void reset() {
    headTagBytesSeen = 0;
    contentLength = -1;
    contentLengthAdjusted = false;
    writer = false;
    forwardPending = false;
    resetSequence++;
  }

  public long getResetSequence() {
    return resetSequence;
  }

  public void resetForForward() {
    if (forwardPending) {
      resetBuffer();
    }
  }

  public void finishForward(boolean successful) {
    if (!forwardPending) {
      return;
    }
    if (successful) {
      resetBuffer();
    } else {
      forwardPending = false;
    }
  }

  // only used by tests
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
    if (isHeadTagWritten() || !isCurrentRequest()) {
      return false;
    }
    if (!wrapper.isContentTypeTextHtml()) {
      // A partial head tag must not span writes of non-HTML content.
      headTagBytesSeen = 0;
      return false;
    }
    return true;
  }

  public boolean isCurrentRequest() {
    return wrapper.isCurrentRequest();
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

  public SnippetInjectingResponseWrapper getContentLengthWrapper() {
    return contentLengthWrapper;
  }
}
