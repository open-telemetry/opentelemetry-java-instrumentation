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

  compileOnly(project(":instrumentation:rmi:bootstrap"))
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
  withType<GroovyCompile>().configureEach {
    options.release.set(null as Int?)
  }
  withType<Test>().configureEach {
    jvmArgs("-Djava.rmi.server.hostname=127.0.0.1")

    // Can only export on Java 9+
    val testJavaVersion =
      gradle.startParameter.projectProperties.get("testJavaVersion")?.let(JavaVersion::toVersion)
        ?: JavaVersion.current()
    if (testJavaVersion.isJava9Compatible) {
      jvmArgs("--add-exports=java.rmi/sun.rmi.server=ALL-UNNAMED")
      jvmArgs("--add-exports=java.rmi/sun.rmi.transport=ALL-UNNAMED")
    }
  }
}
