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

tasks {
  val rmic by registering(Exec::class) {
    dependsOn(testClasses)

    val clazz = "rmi.app.ServerLegacy"

    val rmicBinaryPath = listOf("/bin/rmic", "/../bin/rmic").map {
      File(System.getProperty("java.home"), it).absoluteFile
    }.find { it.isFile() }?.let(File::toString) ?: "rmic"

    commandLine(
      rmicBinaryPath,
      "-g",
      "-keep",
      "-classpath",
      sourceSets.test.get().output.classesDirs.asPath,
      "-d",
      "$buildDir/classes/java/test",
      clazz
    )
  }

  test {
    dependsOn(rmic)
  }
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
