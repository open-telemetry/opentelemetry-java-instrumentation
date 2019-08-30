package datadog.trace.bootstrap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.NoSuchFileException;
import java.security.Permission;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InternalJarURLHandler extends URLStreamHandler {
  private final Map<String, JarEntry> filenameToEntry = new HashMap<>();
  private JarFile bootstrapJarFile;

  InternalJarURLHandler(final String internalJarFileName, final URL bootstrapJarLocation) {
    try {
      if (bootstrapJarLocation != null) {
        bootstrapJarFile = new JarFile(new File(bootstrapJarLocation.toURI()));
        final Enumeration<JarEntry> entries = bootstrapJarFile.entries();
        while (entries.hasMoreElements()) {
          final JarEntry entry = entries.nextElement();

          if (!entry.isDirectory() && entry.getName().startsWith(internalJarFileName + "/")) {
            filenameToEntry.put(entry.getName().substring(internalJarFileName.length()), entry);
          }
        }
      }
    } catch (final URISyntaxException | IOException e) {
      log.error("Unable to read internal jar", e);
    }

    if (filenameToEntry.isEmpty()) {
      log.warn("Internal jar entries found");
    }
  }

  @Override
  protected URLConnection openConnection(final URL url) throws IOException {

    final byte[] bytes;

    final String filename = url.getFile().replaceAll("\\.class$", ".classdata");
    if ("/".equals(filename)) {
      // "/" is used as the default url of the jar
      // This is called by the SecureClassLoader trying to obtain permissions
      bytes = new byte[0];
    } else if (filenameToEntry.containsKey(filename)) {
      final JarEntry entry = filenameToEntry.get(filename);
      bytes = getBytes(bootstrapJarFile.getInputStream(entry));
    } else {
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
