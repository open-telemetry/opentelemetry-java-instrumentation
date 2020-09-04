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

package io.opentelemetry.javaagent.bootstrap;

import static io.opentelemetry.instrumentation.auto.api.WeakMap.Provider.newWeakMap;

import io.opentelemetry.instrumentation.auto.api.WeakMap;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A holder of resources needed by instrumentation. We store them in the bootstrap classloader so
 * instrumentation can store from the agent classloader and apps can retrieve from the app
 * classloader.
 */
public final class HelperResources {

  private static final Logger log = LoggerFactory.getLogger(HelperResources.class);

  private static final WeakMap<ClassLoader, Map<String, URL>> RESOURCES = newWeakMap();

  /** Registers the {@code payload} to be available to instrumentation at {@code path}. */
  public static void register(ClassLoader classLoader, String path, byte[] payload) {
    RESOURCES.putIfAbsent(classLoader, new ConcurrentHashMap<String, URL>());

    final URL url;
    try {
      url = new URL(null, "bytes:///" + path, new BytesHandler(payload));
    } catch (MalformedURLException e) {
      log.debug("Malformed resource path {}, will not be injected.", path, e);
      return;
    }
    RESOURCES.get(classLoader).put(path, url);
  }

  /**
   * Returns a {@link URL} that can be used to retrieve the content of the resource at {@code path},
   * or {@code null} if no resource could be found at {@code path}.
   */
  public static URL load(ClassLoader classLoader, String path) {
    Map<String, URL> map = RESOURCES.get(classLoader);
    if (map == null) {
      return null;
    }

    return map.get(path);
  }

  private static class BytesHandler extends URLStreamHandler {

    private final byte[] payload;

    private BytesHandler(byte[] payload) {
      this.payload = payload;
    }

    @Override
    protected URLConnection openConnection(URL u) {
      return new ByteUrlConnection(u, payload);
    }
  }

  private static class ByteUrlConnection extends URLConnection {
    private final byte[] payload;

    private ByteUrlConnection(URL url, byte[] payload) {
      super(url);
      this.payload = payload;
    }

    @Override
    public void connect() {}

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(payload);
    }
  }

  private HelperResources() {}
}
