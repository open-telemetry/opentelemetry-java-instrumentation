/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.servlet.v3_0.bodycapture;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BufferingHttpServletRequest extends HttpServletRequestWrapper {

  private static final Logger logger = LoggerFactory.getLogger(HttpServletRequestWrapper.class);

  private CharBuffer charBuffer;
  private ByteBuffer byteBuffer;
  private Map<String, List<String>> params = new LinkedHashMap<>();

  public BufferingHttpServletRequest(HttpServletRequest httpServletRequest) {
    super(httpServletRequest);
  }

  @Override
  public ServletInputStream getInputStream() throws IOException {
    ServletInputStream is = super.getInputStream();
    return safeGetInputStream(is);
  }

  @Override
  public String getParameter(String name) {
    String queryStringValue = super.getParameter(name);
    safelyCacheGetParameterCall(name, queryStringValue);
    return queryStringValue;
  }

  private void safelyCacheGetParameterCall(String name, String queryStringValue) {
    if (!isFormContentType()) {
      return;
    }

    try {
      List<String> values = params.getOrDefault(name, new ArrayList<>());
      values.add(queryStringValue);
      params.put(name, values);
    } catch (Exception e) {
      logger.error("Error in getParameter() ", e);
    }
  }

  @Override
  public Map<String, String[]> getParameterMap() {
    Map<String, String[]> result = super.getParameterMap();
    safelyCacheGetParameterMapCall(result);
    return result;
  }

  private void safelyCacheGetParameterMapCall(Map<String, String[]> result) {
    if (!isFormContentType()) {
      return;
    }

    try {
      if (result != null && !result.isEmpty()) {
        if (params.size() != result.size()) {
          params.clear();
          for (Map.Entry<String, String[]> entry : result.entrySet()) {
            List<String> values = params.getOrDefault(entry.getKey(), new ArrayList<>());
            if (entry.getValue() != null) {
              values.addAll(Arrays.asList(entry.getValue()));
              params.put(entry.getKey(), values);
            }
          }
        }
      }
    } catch (Exception e) {
      logger.error("Error in getParameterMap ", e);
    }
  }

  @Override
  public String[] getParameterValues(String name) {
    String[] parameterValues = super.getParameterValues(name);
    safelyCacheGetParameterValues(name, parameterValues);
    return parameterValues;
  }

  private void safelyCacheGetParameterValues(String name, String[] parameterValues) {
    if (!isFormContentType()) {
      return;
    }

    try {
      if (parameterValues != null) {
        List<String> values = params.get(name);
        if (values == null || values.size() != parameterValues.length) {
          values = new ArrayList<>(Arrays.asList(parameterValues));
          params.put(name, values);
        }
      }
    } catch (Exception e) {
      logger.error("Error in getParameterValues ", e);
    }
  }

  private ServletInputStream safeGetInputStream(ServletInputStream is) {
    try {
      String encoding = this.getCharacterEncoding();
      ServletInputStreamWrapper servletInputStreamWrapper = new ServletInputStreamWrapper(is, this);

      try {
        if (encoding != null) {
          Charset charset = Charset.forName(encoding);
          servletInputStreamWrapper.setCharset(charset);
        } else {
          logger.debug("Encoding is not specified in servlet request will default to [ISO-8859-1]");
          Charset charset = Charset.forName(StandardCharsets.ISO_8859_1.name());
          servletInputStreamWrapper.setCharset(charset);
        }
      } catch (IllegalArgumentException var5) {
        logger.warn("Encoding [{}] not recognized. Will default to [ISO-8859-1]", encoding);
        Charset charset = Charset.forName(StandardCharsets.ISO_8859_1.name());
        servletInputStreamWrapper.setCharset(charset);
      }
      return servletInputStreamWrapper;
    } catch (Exception e) {
      logger.error("Error in getInputStream - ", e);
      return is;
    }
  }

  @Override
  public BufferedReader getReader() throws IOException {
    BufferedReader reader = super.getReader();
    try {
      return new BufferedReaderWrapper(reader, this);
    } catch (Exception e) {
      logger.error("Error in getReader - ", e);
      return reader;
    }
  }

  private boolean isFormContentType() {
    String contentType = getContentType();
    // todo use some library to parse
    return contentType != null && contentType.contains("application/x-www-form-urlencoded");
  }

  public Map<String, List<String>> getBufferedParams() {
    return params;
  }

  public synchronized String getBufferedBodyAsString() {
    try {
      try {
        // read the remaining bytes in the input stream..
        ServletInputStream is = getInputStream();
        if (is != null) {
          while (is.read() != -1) ;
        }
      } catch (IllegalStateException e) {
        // read the remaining bytes in the reader..
        BufferedReader br = getReader();
        if (br != null) {
          while (br.read() != -1) ;
        }
      }
    } catch (Exception e) {
      logger.error("Error in reading request body: ", e);
    }
    if (byteBuffer != null) {
      return byteBuffer.getBufferAsString();
    }
    if (charBuffer != null) {
      return charBuffer.getBufferAsString();
    }
    return null;
  }

  public synchronized ByteBuffer getByteBuffer() {
    if (null == byteBuffer) {
      byteBuffer = new ByteBuffer();
    }
    return byteBuffer;
  }

  public synchronized CharBuffer getCharBuffer() {
    if (charBuffer == null) {
      charBuffer = new CharBuffer();
    }
    return charBuffer;
  }

  public static class ServletInputStreamWrapper extends ServletInputStream {

    private static final Logger logger = LoggerFactory.getLogger(ServletInputStreamWrapper.class);

    final ServletInputStream is;
    final BufferingHttpServletRequest bufferingHttpServletRequest;

    ServletInputStreamWrapper(
        ServletInputStream is, BufferingHttpServletRequest bufferingHttpServletRequest) {
      this.is = is;
      this.bufferingHttpServletRequest = bufferingHttpServletRequest;
    }

    void setCharset(Charset charset) {
      bufferingHttpServletRequest.getByteBuffer().setCharset(charset);
    }

    public int read(byte[] b) throws IOException {
      int numRead = this.is.read(b);
      try {
        if (numRead > 0) {
          bufferingHttpServletRequest.getByteBuffer().appendData(b, 0, numRead);
        }
      } catch (Exception e) {
        logger.error("Error in read(byte[] b) - ", e);
      }
      return numRead;
    }

    public int read(byte[] b, int off, int len) throws IOException {
      int numRead = this.is.read(b, off, len);
      try {
        if (numRead > 0) {
          bufferingHttpServletRequest.getByteBuffer().appendData(b, off, off + numRead);
        }
      } catch (Exception e) {
        logger.error("Error in read(byte[] b, int off, int len) - ", e);
      }
      return numRead;
    }

    public int read() throws IOException {
      int read = this.is.read();
      try {
        bufferingHttpServletRequest.getByteBuffer().appendData(read);
      } catch (Exception e) {
        logger.error("Error in read() - ", e);
      }
      return read;
    }

    public int readLine(byte[] b, int off, int len) throws IOException {
      int numRead = this.is.readLine(b, off, len);
      try {
        if (numRead > 0) {
          bufferingHttpServletRequest.getByteBuffer().appendData(b, off, off + numRead);
        }
      } catch (Exception e) {
        logger.error("Error in readLine() - ", e);
      }

      return numRead;
    }

    public long skip(long n) throws IOException {
      return this.is.skip(n);
    }

    public int available() throws IOException {
      return this.is.available();
    }

    public void close() throws IOException {
      this.is.close();
    }

    public void mark(int readlimit) {
      this.is.mark(readlimit);
    }

    public void reset() throws IOException {
      this.is.reset();
    }

    public boolean markSupported() {
      return this.is.markSupported();
    }
  }

  public static class BufferedReaderWrapper extends BufferedReader {

    private static final Logger logger = LoggerFactory.getLogger(BufferedReaderWrapper.class);

    private final BufferedReader reader;
    private final BufferingHttpServletRequest bufferingHttpServletRequest;

    public BufferedReaderWrapper(
        BufferedReader reader, BufferingHttpServletRequest bufferingHttpServletRequest) {
      super(reader);
      this.reader = reader;
      this.bufferingHttpServletRequest = bufferingHttpServletRequest;
    }

    public int read() throws IOException {
      int read = this.reader.read();
      try {
        if (read >= 0) {
          bufferingHttpServletRequest.getCharBuffer().appendData(read);
        }
      } catch (Exception e) {
        logger.error("Error in read() - ", e);
      }
      return read;
    }

    public int read(char[] cbuf, int off, int len) throws IOException {
      int read = this.reader.read(cbuf, off, len);
      try {
        if (read > 0) {
          bufferingHttpServletRequest.getCharBuffer().appendData(cbuf, off, off + read);
        }
      } catch (Exception e) {
        logger.error("Error in read(char[] cbuf, int off, int len) - ", e);
      }
      return read;
    }

    public String readLine() throws IOException {
      String read = this.reader.readLine();
      if (read == null) {
        return null;
      } else {
        try {
          CharBuffer charBuffer = bufferingHttpServletRequest.getCharBuffer();
          charBuffer.appendData(read);
        } catch (Exception e) {
          logger.error("Error in readLine() - ", e);
        }
        return read;
      }
    }

    public long skip(long n) throws IOException {
      return this.reader.skip(n);
    }

    public boolean ready() throws IOException {
      return this.reader.ready();
    }

    public boolean markSupported() {
      return this.reader.markSupported();
    }

    public void mark(int readAheadLimit) throws IOException {
      this.reader.mark(readAheadLimit);
    }

    public void reset() throws IOException {
      this.reader.reset();
    }

    public void close() throws IOException {
      this.reader.close();
    }

    public int read(java.nio.CharBuffer target) throws IOException {
      int initPos = target.position();
      int read = this.reader.read(target);
      try {
        if (read > 0) {
          CharBuffer charBuffer = bufferingHttpServletRequest.getCharBuffer();

          for (int i = initPos; i < initPos + read; ++i) {
            charBuffer.appendData(target.get(i));
          }
        }
      } catch (Exception e) {
        logger.error("Error in read(target) - ", e);
      }
      return read;
    }

    public int read(char[] cbuf) throws IOException {
      int read = this.reader.read(cbuf);
      try {
        if (read > 0) {
          bufferingHttpServletRequest.getCharBuffer().appendData(cbuf, 0, read);
        }
      } catch (Exception e) {
        logger.error("Error in read(char[]) - ", e);
      }
      return read;
    }
  }
}
