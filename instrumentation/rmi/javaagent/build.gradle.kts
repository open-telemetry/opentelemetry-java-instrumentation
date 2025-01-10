plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    coreJdk()
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
  withType<JavaCompile>().configureEach {
    options.release.set(null as Int?)
  }
  withType<Test>().configureEach {
    jvmArgs("-Djava.rmi.server.hostname=127.0.0.1")
  }
}
