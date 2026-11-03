import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.library-instrumentation")
}

val mrJarVersions = listOf(17)
val testJavaVersion = otelProps.testJavaVersion ?: JavaVersion.current()

dependencies {
  implementation(project(":instrumentation-api"))
  implementation(project(":instrumentation-api-incubator"))

  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("io.github.netmikey.logunit:logunit-jul")
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

testing.suites {
  register<JvmTestSuite>("testJava17") {
    executions {
      configureEach {
        dependsOn("compileTestJava17Java")
        systemProperty("metadataConfig", "Java17")
        if (!testJavaVersion.isCompatibleWith(JavaVersion.VERSION_17)) {
          enabled = false
        }
      }

      configureDefault {
        filter {
          excludeTestsMatching("*G1GcMemoryMetricTest")
          excludeTestsMatching("*SerialGcMemoryMetricTest")
          excludeTestsMatching("*PsGcMemoryMetricTest")
        }
      }

      register("G1") {
        filter {
          includeTestsMatching("*G1GcMemoryMetricTest*")
        }
        include("**/*G1GcMemoryMetricTest.*")
        jvmArgs("-XX:+UseG1GC")
      }

      register("PS") {
        filter {
          includeTestsMatching("*PsGcMemoryMetricTest*")
        }
        include("**/*PsGcMemoryMetricTest.*")
        jvmArgs("-XX:+UseParallelGC")
      }

      register("Serial") {
        filter {
          includeTestsMatching("*SerialGcMemoryMetricTest*")
        }
        include("**/*SerialGcMemoryMetricTest.*")
        jvmArgs("-XX:+UseSerialGC")
      }
    }
  }

  register<JvmTestSuite>("testJava21") {
    executions {
      configureEach {
        dependsOn("compileTestJava21Java")
        systemProperty("metadataConfig", "Java21")
        if (!testJavaVersion.isCompatibleWith(JavaVersion.VERSION_21)) {
          enabled = false
        }
      }
    }
  }
}

configurations {
  named("testJava17Implementation") {
    extendsFrom(configurations["testImplementation"])
  }
  named("testJava17RuntimeOnly") {
    extendsFrom(configurations["testRuntimeOnly"])
  }
  named("testJava21Implementation") {
    extendsFrom(configurations["testImplementation"])
  }
  named("testJava21RuntimeOnly") {
    extendsFrom(configurations["testRuntimeOnly"])
  }
}

dependencies {
  add("testJava17Implementation", sourceSets.test.get().output)
  add("testJava17Implementation", sourceSets["java17"].output)
  add("testJava17Implementation", sourceSets.main.get().output)

  add("testJava21Implementation", sourceSets.test.get().output)
  add("testJava21Implementation", sourceSets["java17"].output)
  add("testJava21Implementation", sourceSets["testJava17"].output)
  add("testJava21Implementation", sourceSets.main.get().output)
}

tasks {
  // Configure testJava17 compilation for Java 17
  named<JavaCompile>("compileTestJava17Java") {
    dependsOn("compileJava17Java")
    sourceCompatibility = "17"
    targetCompatibility = "17"
    options.release.set(17)
  }

  // Configure testJava21 compilation for Java 21
  named<JavaCompile>("compileTestJava21Java") {
    dependsOn("compileJava17Java", "compileTestJava17Java")
    sourceCompatibility = "21"
    targetCompatibility = "21"
    options.release.set(21)
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

  check {
    dependsOn(testing.suites)
  }
}
