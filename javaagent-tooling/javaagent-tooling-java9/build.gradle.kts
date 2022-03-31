plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.javaagent"

dependencies {
  implementation(project(":javaagent-bootstrap"))

  implementation("net.bytebuddy:byte-buddy-dep")

  // Used by byte-buddy but not brought in as a transitive dependency.
  compileOnly("com.google.code.findbugs:annotations")

  testImplementation("net.bytebuddy:byte-buddy-agent")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_1_9)
}

tasks {
  compileJava {
    with(options) {
      // Because this module targets Java 9, we trigger this compiler bug which was fixed but not
      // backported to Java 9 compilation.
      // https://bugs.openjdk.java.net/browse/JDK-8209058
      compilerArgs.add("-Xlint:none")
    }
  }
}
