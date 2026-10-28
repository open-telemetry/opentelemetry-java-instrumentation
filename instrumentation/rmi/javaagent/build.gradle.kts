plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    coreJdk.set(true)
  }
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  bootstrap(project(":instrumentation:rmi:bootstrap"))

  implementation(project(":javaagent-tooling:javaagent-tooling-java9"))
}

// We cannot use "--release" javac option here because that will forbid importing "sun.rmi" package.
// We also can't seem to use the toolchain without the "--release" option. So disable everything.

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
  toolchain {
    languageVersion.set(null as JavaLanguageVersion?)
  }
}

tasks {
  val testPreviewSemconv = register<Test>("testPreviewSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Djava.rmi.server.hostname=127.0.0.1", "-Dotel.semconv-stability.preview=rpc")
  }

  val testBothSemconv = register<Test>("testBothSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Djava.rmi.server.hostname=127.0.0.1", "-Dotel.semconv-stability.preview=rpc/dup")
  }

  withType<JavaCompile>().configureEach {
    options.release.set(null as Int?)
  }
  test {
    jvmArgs("-Djava.rmi.server.hostname=127.0.0.1")
  }
  check {
    dependsOn(testPreviewSemconv, testBothSemconv)
  }
}
