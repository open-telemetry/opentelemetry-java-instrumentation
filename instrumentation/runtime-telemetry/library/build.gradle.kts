plugins {
  id("otel.library-instrumentation")
}

val mrJarVersions = listOf(17)

dependencies {
  implementation(project(":instrumentation-api"))
  implementation(project(":instrumentation-api-incubator"))

  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("io.github.netmikey.logunit:logunit-jul:1.1.3")
}

sourceSets {
  create("testJava17") {
    java {
      setSrcDirs(listOf("src/testJava17/java"))
    }
  }
}

for (version in mrJarVersions) {
  sourceSets {
    create("java$version") {
      java {
        setSrcDirs(listOf("src/main/java$version"))
      }
    }
  }

  tasks {
    named<JavaCompile>("compileJava${version}Java") {
      sourceCompatibility = "$version"
      targetCompatibility = "$version"
      options.release.set(version)
    }
  }

  configurations {
    named("java${version}Implementation") {
      extendsFrom(configurations["implementation"])
    }
    named("java${version}CompileOnly") {
      extendsFrom(configurations["compileOnly"])
    }
  }

  dependencies {
    // Common to reference classes in main sourceset from Java 17 one
    add("java${version}Implementation", files(sourceSets.main.get().output.classesDirs))
  }
}

// Configure testJava17 dependencies after java17 sourceset is created
configurations {
  named("testJava17Implementation") {
    extendsFrom(configurations["testImplementation"])
  }
  named("testJava17RuntimeOnly") {
    extendsFrom(configurations["testRuntimeOnly"])
  }
}

dependencies {
  add("testJava17Implementation", sourceSets.test.get().output)
  add("testJava17Implementation", sourceSets["java17"].output)
  add("testJava17Implementation", sourceSets.main.get().output)
}

tasks {
  // Configure testJava17 compilation for Java 17
  named<JavaCompile>("compileTestJava17Java") {
    dependsOn("compileJava17Java")
    sourceCompatibility = "17"
    targetCompatibility = "17"
    options.release.set(17)
  }

  withType(Jar::class) {
    val sourcePathProvider = if (name == "jar") {
      { ss: SourceSet? -> ss?.output }
    } else if (name == "sourcesJar") {
      { ss: SourceSet? -> ss?.java }
    } else {
      { project.objects.fileCollection() }
    }

    for (version in mrJarVersions) {
      into("META-INF/versions/$version") {
        from(sourcePathProvider(sourceSets["java$version"]))
      }
    }
    manifest.attributes(
      "Multi-Release" to "true",
    )
  }

  // GC-specific tests that require Java 17+
  val testG1 by registering(Test::class) {
    dependsOn("compileTestJava17Java")
    testClassesDirs = sourceSets["testJava17"].output.classesDirs
    classpath = sourceSets["testJava17"].runtimeClasspath
    filter {
      includeTestsMatching("*G1GcMemoryMetricTest*")
    }
    include("**/*G1GcMemoryMetricTest.*")
    jvmArgs("-XX:+UseG1GC")
  }

  val testPS by registering(Test::class) {
    dependsOn("compileTestJava17Java")
    testClassesDirs = sourceSets["testJava17"].output.classesDirs
    classpath = sourceSets["testJava17"].runtimeClasspath
    filter {
      includeTestsMatching("*PsGcMemoryMetricTest*")
    }
    include("**/*PsGcMemoryMetricTest.*")
    jvmArgs("-XX:+UseParallelGC")
  }

  val testSerial by registering(Test::class) {
    dependsOn("compileTestJava17Java")
    testClassesDirs = sourceSets["testJava17"].output.classesDirs
    classpath = sourceSets["testJava17"].runtimeClasspath
    filter {
      includeTestsMatching("*SerialGcMemoryMetricTest*")
    }
    include("**/*SerialGcMemoryMetricTest.*")
    jvmArgs("-XX:+UseSerialGC")
  }

  // Run other Java 17 tests (not GC-specific)
  val testJava17 by registering(Test::class) {
    dependsOn("compileTestJava17Java")
    testClassesDirs = sourceSets["testJava17"].output.classesDirs
    classpath = sourceSets["testJava17"].runtimeClasspath
    filter {
      excludeTestsMatching("*G1GcMemoryMetricTest")
      excludeTestsMatching("*SerialGcMemoryMetricTest")
      excludeTestsMatching("*PsGcMemoryMetricTest")
    }
  }

  test {
    // Java 8 tests only
  }

  val testJavaVersion =
    gradle.startParameter.projectProperties.get("testJavaVersion")?.let(JavaVersion::toVersion)
      ?: JavaVersion.current()
  if (!testJavaVersion.isCompatibleWith(JavaVersion.VERSION_17)) {
    named("testG1", Test::class).configure {
      enabled = false
    }
    named("testPS", Test::class).configure {
      enabled = false
    }
    named("testSerial", Test::class).configure {
      enabled = false
    }
    named("testJava17", Test::class).configure {
      enabled = false
    }
  }

  check {
    dependsOn(testJava17, testG1, testPS, testSerial)
  }
}
