package datadog.trace.bootstrap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.NoSuchFileException;
import java.security.Permission;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InternalJarURLHandler extends URLStreamHandler {
  private final Map<String, byte[]> filenameToBytes = new HashMap<>();

  InternalJarURLHandler(
      final String internalJarFileName, final ClassLoader classloaderForJarResource) {

    // "/" is used as the default url of the jar
    // This is called by the SecureClassLoader trying to obtain permissions
    filenameToBytes.put("/", new byte[] {});

    final InputStream jarStream =
        classloaderForJarResource.getResourceAsStream(internalJarFileName);

    if (jarStream != null) {
      try (final JarInputStream inputStream = new JarInputStream(jarStream)) {
        JarEntry entry = inputStream.getNextJarEntry();

        while (entry != null) {
          filenameToBytes.put("/" + entry.getName(), getBytes(inputStream));

          entry = inputStream.getNextJarEntry();
        }

      } catch (final IOException e) {
        log.error("Unable to read internal jar", e);
      }
    } else {
      log.error("Internal jar not found");
    }
  }

  @Override
  protected URLConnection openConnection(final URL url) throws IOException {
    final byte[] bytes = filenameToBytes.get(url.getFile());

    if (bytes == null) {
      throw new NoSuchFileException(url.getFile(), null, url.getFile() + " not in internal jar");
    }

    return new InternalJarURLConnection(url, bytes);
  }

  /**
   * Standard "copy InputStream to byte[]" implementation using a ByteArrayOutputStream
   *
   * <p>IOUtils.toByteArray() or Java 9's InputStream.readAllBytes() could be replacements if they
   * were available
   *
   * <p>This can be optimized using the JarEntry's size(), but its not always available
   *
   * @param inputStream stream to read
   * @return a byte[] from the inputstream
   */
  private static byte[] getBytes(final InputStream inputStream) throws IOException {
    final byte[] buffer = new byte[4096];

    int bytesRead = inputStream.read(buffer, 0, buffer.length);
    try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      while (bytesRead != -1) {
        outputStream.write(buffer, 0, bytesRead);

        bytesRead = inputStream.read(buffer, 0, buffer.length);
      }

      return outputStream.toByteArray();
    }
  }

  private static class InternalJarURLConnection extends URLConnection {
    private final byte[] bytes;

    private InternalJarURLConnection(final URL url, final byte[] bytes) {
      super(url);
      this.bytes = bytes;
    }

    @Override
    public void connect() throws IOException {
      connected = true;
    }

    @Override
    public InputStream getInputStream() throws IOException {
      return new ByteArrayInputStream(bytes);
    }

    @Override
    public Permission getPermission() {
      // No permissions needed because all classes are in memory
      return null;
    }
  }
}
