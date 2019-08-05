package datadog.trace.bootstrap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.NoSuchFileException;
import java.security.Permission;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import lombok.extern.slf4j.Slf4j;

/**
 * Classloader used to run the core datadog agent.
 *
 * <p>It is built around the concept of a jar inside another jar. This classloader loads the files
 * of the internal jar to load classes and resources.
 */
@Slf4j
public class DatadogClassLoader extends URLClassLoader {
  static {
    ClassLoader.registerAsParallelCapable();
  }

  // Calling java.lang.instrument.Instrumentation#appendToBootstrapClassLoaderSearch
  // adds a jar to the bootstrap class lookup, but not to the resource lookup.
  // As a workaround, we keep a reference to the bootstrap jar
  // to use only for resource lookups.
  private final BootstrapClassLoaderProxy bootstrapProxy;
  /**
   * Construct a new DatadogClassLoader
   *
   * @param bootstrapJarLocation Used for resource lookups.
   * @param internalJarFileName File name of the internal jar
   * @param parent Classloader parent. Should null (bootstrap), or the platform classloader for java
   *     9+.
   */
  public DatadogClassLoader(
      final URL bootstrapJarLocation, final String internalJarFileName, final ClassLoader parent) {
    super(new URL[] {}, parent);
    bootstrapProxy = new BootstrapClassLoaderProxy(new URL[] {bootstrapJarLocation});

    if (internalJarFileName != null) { // some tests pass null
      try {
        // The fields of the URL are mostly dummy.  InternalJarURLHandler is the only important
        // field.  If extending this class from Classloader instead of URLClassloader required less
        // boilerplate it could be used and the need for dummy fields would be reduced

        final URL internalJarURL =
            new URL(
                "x-internal-jar",
                null,
                0,
                "/",
                new InternalJarURLHandler(internalJarFileName, bootstrapProxy));

        addURL(internalJarURL);
      } catch (final MalformedURLException e) {
        // This can't happen with current URL constructor
        log.error("URL malformed.  Unsupported JDK?", e);
      }
    }
  }

  @Override
  public URL getResource(final String resourceName) {
    final URL bootstrapResource = bootstrapProxy.getResource(resourceName);
    if (null == bootstrapResource) {
      return super.getResource(resourceName);
    } else {
      return bootstrapResource;
    }
  }

  /**
   * @param className binary name of class
   * @return true if this loader has attempted to load the given class
   */
  public boolean hasLoadedClass(final String className) {
    return findLoadedClass(className) != null;
  }

  public BootstrapClassLoaderProxy getBootstrapProxy() {
    return bootstrapProxy;
  }

  /**
   * A stand-in for the bootstrap classloader. Used to look up bootstrap resources and resources
   * appended by instrumentation.
   *
   * <p>This class is thread safe.
   */
  public static final class BootstrapClassLoaderProxy extends URLClassLoader {
    static {
      ClassLoader.registerAsParallelCapable();
    }

    public BootstrapClassLoaderProxy(final URL[] urls) {
      super(urls, null);
    }

    @Override
    public void addURL(final URL url) {
      super.addURL(url);
    }

    @Override
    protected Class<?> findClass(final String name) throws ClassNotFoundException {
      throw new ClassNotFoundException(name);
    }
  }

  protected static class InternalJarURLHandler extends URLStreamHandler {
    private final Map<String, byte[]> filenameToBytes = new HashMap<>();

    public InternalJarURLHandler(
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
  }

  protected static class InternalJarURLConnection extends URLConnection {
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

  /**
   * Standard "copy InputStream to byte[]" implementation using a ByteArrayOutputStream
   *
   * <p>IOUtils.toByteArray() or Java 9's InputStream.readAllBytes() could be replacements if they
   * were available
   *
   * <p>This can be optimized using the JarEntry's size(), but its not always available
   *
   * @param inputStream
   * @return a byte[] from the inputstream
   * @throws IOException
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
}
