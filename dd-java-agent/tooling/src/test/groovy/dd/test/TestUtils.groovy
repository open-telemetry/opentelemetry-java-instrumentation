package dd.test

import dd.trace.Instrumenter
import io.opentracing.ActiveSpan
import io.opentracing.Tracer
import io.opentracing.util.GlobalTracer
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.agent.builder.AgentBuilder

import java.lang.reflect.Field
import java.util.concurrent.Callable

import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith
import static org.assertj.core.api.Assertions.assertThat

class TestUtils {

  static addByteBuddyAgent() {
    AgentBuilder builder =
      new AgentBuilder.Default()
        .disableClassFormatChanges()
        .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
//        .with(AgentBuilder.Listener.StreamWriting.toSystemError())
        .ignore(nameStartsWith("dd.inst"))

    def instrumenters = ServiceLoader.load(Instrumenter)
    for (final Instrumenter instrumenter : instrumenters) {
      System.err.println("Instrumenting with " + instrumenter)
      builder = instrumenter.instrument(builder)
    }
    builder.installOn(ByteBuddyAgent.install())
  }

  static registerOrReplaceGlobalTracer(Tracer tracer) {
    try {
      GlobalTracer.register(tracer)
    } catch (final Exception e) {
      // Force it anyway using reflection
      final Field field = GlobalTracer.getDeclaredField("tracer")
      field.setAccessible(true)
      field.set(null, tracer)
    }
    assertThat(GlobalTracer.isRegistered()).isTrue()
  }

  static runUnderTrace(final String rootOperationName, Callable r) {
    ActiveSpan rootSpan = GlobalTracer.get().buildSpan(rootOperationName).startActive()
    try {
      return r.call()
    } finally {
      rootSpan.deactivate()
    }
  }

  /** com.foo.Bar -> com/foo/Bar.class */
  static String getResourceName(Class<?> clazz) {
    return clazz.getName().replace('.', '/') + ".class"
  }

  /**
   * Create a temporary jar on the filesystem with the bytes of the given classes.
   *
   * <p>The jar file will be removed when the jvm exits.
   *
   * @param classes classes to package into the jar.
   * @return the location of the newly created jar.
   * @throws IOException
   */
  static URL createJarWithClasses(Class<?>... classes) throws IOException {
    final File tmpJar = File.createTempFile(UUID.randomUUID().toString() + "", ".jar")
    tmpJar.deleteOnExit()

    final Manifest manifest = new Manifest()
    JarOutputStream target = new JarOutputStream(new FileOutputStream(tmpJar), manifest)
    for (Class<?> clazz : classes) {
      addToJar(clazz, target)
    }
    target.close()

    return tmpJar.toURI().toURL()
  }

  static void addToJar(Class<?> clazz, JarOutputStream jarOutputStream) throws IOException {
    InputStream inputStream = null
    try {
      JarEntry entry = new JarEntry(getResourceName(clazz))
      jarOutputStream.putNextEntry(entry)
      inputStream = clazz.getClassLoader().getResourceAsStream(getResourceName(clazz))

      byte[] buffer = new byte[1024]
      while (true) {
        int count = inputStream.read(buffer)
        if (count == -1) {
          break
        }
        jarOutputStream.write(buffer, 0, count)
      }
      jarOutputStream.closeEntry()
    } finally {
      if (inputStream != null) {
        inputStream.close()
      }
    }
  }
}
