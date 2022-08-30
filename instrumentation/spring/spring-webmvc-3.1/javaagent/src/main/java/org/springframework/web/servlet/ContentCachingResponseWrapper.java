package org.springframework.web.servlet;

/**
 * Create a new ContentCachingResponseWrapper for the given servlet response.
 *
 * @param response the original servlet response
 */
import org.springframework.web.util.WebUtils;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

public class ContentCachingResponseWrapper extends HttpServletResponseWrapper {
  private final ByteArrayOutputStream content = new ByteArrayOutputStream(1024);

  private final ServletOutputStream outputStream = new ResponseServletOutputStream();

  private PrintWriter writer;

  private int statusCode = HttpServletResponse.SC_OK;
  public ContentCachingResponseWrapper(HttpServletResponse response) {
    super(response);
  }

  @Override
  public void setStatus(int sc) {
    super.setStatus(sc);
    this.statusCode = sc;
  }

  @SuppressWarnings("deprecation")
  @Override
  public void setStatus(int sc, String sm) {
    super.setStatus(sc, sm);
    this.statusCode = sc;
  }

  @Override
  public void sendError(int sc) throws IOException {
    copyBodyToResponse();
    super.sendError(sc);
    this.statusCode = sc;
  }

  @Override
  public void sendError(int sc, String msg) throws IOException {
    copyBodyToResponse();
    super.sendError(sc, msg);
    this.statusCode = sc;
  }

  @Override
  public void sendRedirect(String location) throws IOException {
    copyBodyToResponse();
    super.sendRedirect(location);
  }

  @Override
  public ServletOutputStream getOutputStream() {
    return this.outputStream;
  }

  @Override
  public PrintWriter getWriter() throws IOException {
    if (this.writer == null) {
      String characterEncoding = getCharacterEncoding();
      this.writer =
          (characterEncoding != null
              ? new ResponsePrintWriter(characterEncoding)
              : new ResponsePrintWriter(WebUtils.DEFAULT_CHARACTER_ENCODING));
    }
    return this.writer;
  }

  @Override
  public void resetBuffer() {
    this.content.reset();
  }

  @Override
  public void reset() {
    super.reset();
    this.content.reset();
  }

  /** Return the status code as specified on the response. */
  public int getStatusCode() {
    return this.statusCode;
  }

  /** Return the cached response content as a byte array. */
  public byte[] getContentAsByteArray() {
    return this.content.toByteArray();
  }

  void copyBodyToResponse() throws IOException {
    if (this.content.size() > 0) {
      getResponse().setContentLength(this.content.size());
      getResponse().getOutputStream().write(this.content.toByteArray());
      this.content.reset();
    }
  }

  private class ResponseServletOutputStream extends ServletOutputStream {

    @Override
    public void write(int b) throws IOException {
      content.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      content.write(b, off, len);
    }

    @Override
    public boolean isReady() {
      return false;
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {}
  }

  private class ResponsePrintWriter extends PrintWriter {

    public ResponsePrintWriter(String characterEncoding) throws UnsupportedEncodingException {
      super(new OutputStreamWriter(content, characterEncoding));
    }

    @Override
    public void write(char[] buf, int off, int len) {
      super.write(buf, off, len);
      super.flush();
    }

    @Override
    public void write(String s, int off, int len) {
      super.write(s, off, len);
      super.flush();
    }

    @Override
    public void write(int c) {
      super.write(c);
      super.flush();
    }
  }
}

