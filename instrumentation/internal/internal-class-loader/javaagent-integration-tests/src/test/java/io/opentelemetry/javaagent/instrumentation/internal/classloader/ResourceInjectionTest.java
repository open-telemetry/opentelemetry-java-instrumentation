package io.opentelemetry.javaagent.instrumentation.internal.classloader;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.time.Duration;
import java.util.Enumeration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static io.opentelemetry.instrumentation.test.utils.GcUtils.awaitGc;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;


class ResourceInjectionTest {

  @Test
  @SuppressWarnings("UnnecessaryAsync")
  void resourcesInjectedToNonDelegatingClassLoader()
      throws IOException, ClassNotFoundException, InterruptedException, TimeoutException {
    String resourceName = "test-resources/test-resource.txt";
    URL[] urls = {ResourceInjectionTest.class.getProtectionDomain().getCodeSource().getLocation()};
    AtomicReference<URLClassLoader> emptyLoader = new AtomicReference<>(new URLClassLoader(urls, null));

    Enumeration<URL> resourceUrls = emptyLoader.get().getResources(resourceName);
    assertThat(resourceUrls.hasMoreElements()).isFalse();

    URLClassLoader notInjectedLoader = new URLClassLoader(urls, null);

    // this triggers resource injection
    emptyLoader.get().loadClass(ResourceInjectionTest.class.getName());

    for (int i = 0; i < 2; i++) {

      URL test = ( resourceUrls.asIterator();
      if (i == 0) {
        assertThat(test).isEqualTo("Hello world!");
      } else {
        assertThat(test).isEqualTo("Hello there");
      }
    }
    assertThat(resourceUrls.hasMoreElements()).isFalse();


//    resourceUrls = (Enumeration<URL>) Collections.list(emptyLoader.get().getResources(resourceName));
//    assertThat(resourceUrls.).isEqualTo(2);
//    assertThat(list.get(0).openStream().toString().trim()).isEqualTo("Hello world!");
//    assertThat(list.get(1).openStream().toString().trim()).isEqualTo("Hello there");

    assertThat(notInjectedLoader.getResources(resourceName).hasMoreElements()).isFalse();

    // references to emptyloader are gone
    emptyLoader.get().close();
    WeakReference<URLClassLoader> ref = new WeakReference<>(emptyLoader.get());
    emptyLoader.set(null);

    awaitGc(ref, Duration.ofSeconds(10));

    assertThat(ref.get()).isNull();
  }

}
