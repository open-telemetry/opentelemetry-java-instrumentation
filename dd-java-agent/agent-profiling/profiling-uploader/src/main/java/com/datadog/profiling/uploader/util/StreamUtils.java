package com.datadog.profiling.uploader.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.openjdk.jmc.common.io.IOToolkit;

/** A collection of I/O stream related helper methods */
public final class StreamUtils {

  // JMC's IOToolkit hides this from us...
  static final int ZIP_MAGIC[] = new int[] {80, 75, 3, 4};
  static final int GZ_MAGIC[] = new int[] {31, 139};
  /**
   * Consumes array or bytes along with offset and length and turns it into something usable.
   *
   * <p>Main idea here is that we may end up having array with valuable data siting somehere in the
   * middle and we can avoid additional copies by allowing user to deal with this directly and
   * convert it into whatever format it needs in most efficient way.
   *
   * @param <T> result type
   */
  @FunctionalInterface
  public interface BytesConsumer<T> {
    T consume(byte[] bytes, int offset, int length);
  }

  /**
   * Read a stream into a consumer gzip-compressing content. If the stream is already compressed
   * (gzip, zip) the original data will be returned.
   *
   * @param is the input stream
   * @return zipped contents of the input stream or the the original content if the stream is
   *     already compressed
   * @throws IOException
   */
  public static <T> T gzipStream(
      InputStream is, final int expectedSize, final BytesConsumer<T> consumer) throws IOException {
    is = ensureMarkSupported(is);
    if (isCompressed(is)) {
      return readStream(is, expectedSize, consumer);
    } else {
      final FastByteArrayOutputStream baos = new FastByteArrayOutputStream(expectedSize);
      try (final OutputStream zipped = new GZIPOutputStream(baos)) {
        copy(is, zipped);
      }
      return baos.consume(consumer);
    }
  }

  /**
   * Read a stream into a consumer.
   *
   * <p>Note: the idea here comes from Guava's {@link com.google.common.io.ByteStreams}, but we
   * cannot use that directly because it is not public and is not flexible enough.
   *
   * @param is the input stream
   * @param expectedSize expected result size to preallocate buffers
   * @param consumer consumer to convert byte array to result
   * @return the stream data
   * @throws IOException
   */
  public static <T> T readStream(
      final InputStream is, final int expectedSize, final BytesConsumer<T> consumer)
      throws IOException {
    final byte[] bytes = new byte[expectedSize];
    int remaining = expectedSize;

    while (remaining > 0) {
      final int offset = expectedSize - remaining;
      final int read = is.read(bytes, offset, remaining);
      if (read == -1) {
        // end of stream before reading expectedSize bytes just return the bytes read so far
        // 'offset' here is offset in 'bytes' buffer - which essentially represents length of data
        // read so far.
        return consumer.consume(bytes, 0, offset);
      }
      remaining -= read;
    }

    // the stream was longer, so read the rest manually
    final List<BufferChunk> additionalChunks = new ArrayList<>();
    int additionalChunksLength = 0;

    while (true) {
      final BufferChunk chunk = new BufferChunk(Math.max(32, is.available()));
      final int readLength = chunk.readFrom(is);
      if (readLength < 0) {
        break;
      } else {
        additionalChunks.add(chunk);
        additionalChunksLength += readLength;
      }
    }

    // now assemble resulting array
    final byte[] result = new byte[bytes.length + additionalChunksLength];
    System.arraycopy(bytes, 0, result, 0, bytes.length);
    int offset = bytes.length;
    for (final BufferChunk chunk : additionalChunks) {
      offset += chunk.appendToArray(result, offset);
    }
    return consumer.consume(result, 0, result.length);
  }

  private static class BufferChunk {

    private int size = 0;
    private final byte[] buf;

    public BufferChunk(final int initialSize) {
      buf = new byte[initialSize];
    }

    public int readFrom(final InputStream is) throws IOException {
      size = is.read(buf, 0, buf.length);
      return size;
    }

    public int appendToArray(final byte[] array, final int offset) {
      System.arraycopy(buf, 0, array, offset, size);
      return size;
    }
  }

  // Helper ByteArrayOutputStream that avoids some data copies
  private static final class FastByteArrayOutputStream extends ByteArrayOutputStream {

    public FastByteArrayOutputStream(final int size) {
      super(size);
    }

    /**
     * ByteArrayOutputStream's API doesn't allow us to get data without a copy. We add this method
     * to support this.
     */
    <T> T consume(final BytesConsumer<T> consumer) {
      return consumer.consume(buf, 0, count);
    }
  }

  /**
   * Copy an input stream into an output stream
   *
   * @param is input
   * @param os output
   * @throws IOException
   */
  private static void copy(final InputStream is, final OutputStream os) throws IOException {
    int length;
    final byte[] buffer = new byte[8192];
    while ((length = is.read(buffer)) > 0) {
      os.write(buffer, 0, length);
    }
  }

  /**
   * Check whether the stream is compressed using a supported format
   *
   * @param is input stream; must support {@linkplain InputStream#mark(int)}
   * @return {@literal true} if the stream is compressed in a supported format
   * @throws IOException
   */
  private static boolean isCompressed(final InputStream is) throws IOException {
    checkMarkSupported(is);
    return isGzip(is) || isZip(is);
  }

  /**
   * Check whether the stream represents GZip data
   *
   * @param is input stream; must support {@linkplain InputStream#mark(int)}
   * @return {@literal true} if the stream represents GZip data
   * @throws IOException
   */
  private static boolean isGzip(final InputStream is) throws IOException {
    checkMarkSupported(is);
    is.mark(GZ_MAGIC.length);
    try {
      return IOToolkit.hasMagic(is, GZ_MAGIC);
    } finally {
      is.reset();
    }
  }

  /**
   * Check whether the stream represents Zip data
   *
   * @param is input stream; must support {@linkplain InputStream#mark(int)}
   * @return {@literal true} if the stream represents Zip data
   * @throws IOException
   */
  private static boolean isZip(final InputStream is) throws IOException {
    checkMarkSupported(is);
    is.mark(ZIP_MAGIC.length);
    try {
      return IOToolkit.hasMagic(is, ZIP_MAGIC);
    } finally {
      is.reset();
    }
  }

  private static InputStream ensureMarkSupported(InputStream is) {
    if (!is.markSupported()) {
      is = new BufferedInputStream(is);
    }
    return is;
  }

  private static void checkMarkSupported(final InputStream is) throws IOException {
    if (!is.markSupported()) {
      throw new IOException("Can not check headers on streams not supporting mark() method");
    }
  }
}
