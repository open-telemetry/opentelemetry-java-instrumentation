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
    dependencies {
      implementation(sourceSets.test.get().output)
      implementation(sourceSets["java17"].output)
      implementation(sourceSets.main.get().output)
    }

    project.configurations.named(sources.implementationConfigurationName) {
      extendsFrom(configurations["testImplementation"])
    }
    project.configurations.named(sources.runtimeOnlyConfigurationName) {
      extendsFrom(configurations["testRuntimeOnly"])
    }
    project.tasks.named<JavaCompile>(sources.compileJavaTaskName) {
      sourceCompatibility = "17"
      targetCompatibility = "17"
      options.release.set(17)
    }
  }

  register<JvmTestSuite>("testJava21") {
    dependencies {
      implementation(sourceSets.test.get().output)
      implementation(sourceSets["java17"].output)
      implementation(sourceSets["testJava17"].output)
      implementation(sourceSets.main.get().output)
    }

    project.configurations.named(sources.implementationConfigurationName) {
      extendsFrom(configurations["testImplementation"])
    }
    project.configurations.named(sources.runtimeOnlyConfigurationName) {
      extendsFrom(configurations["testRuntimeOnly"])
    }
    project.tasks.named<JavaCompile>(sources.compileJavaTaskName) {
      sourceCompatibility = "21"
      targetCompatibility = "21"
      options.release.set(21)
    }
  }

  executions("testJava17") {
    configureEach {
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

  executions("testJava21") {
    configureEach {
      systemProperty("metadataConfig", "Java21")
      if (!testJavaVersion.isCompatibleWith(JavaVersion.VERSION_21)) {
        enabled = false
      }
    }
  }
}

tasks {
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
