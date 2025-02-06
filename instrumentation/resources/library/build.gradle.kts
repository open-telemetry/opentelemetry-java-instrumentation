plugins {
  id("otel.sdk-extension")
}

val mrJarVersions = listOf(9, 11)

dependencies {
  implementation("io.opentelemetry:opentelemetry-sdk-common")
  implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
  implementation("io.opentelemetry.semconv:opentelemetry-semconv")

  annotationProcessor("com.google.auto.service:auto-service")
  compileOnly("com.google.auto.service:auto-service-annotations")
  testCompileOnly("com.google.auto.service:auto-service-annotations")
  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-incubator")

  testImplementation("org.junit.jupiter:junit-jupiter-api")
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
    // Common to reference classes in main sourceset from Java 9 one (e.g., to return a common interface)
    add("java${version}Implementation", files(sourceSets.main.get().output.classesDirs))
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
}

testing {
  suites {
    // Security Manager tests involve setup that can poison the environment for other tests
    val testSecurityManager by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":instrumentation:resources:library"))
        implementation("io.opentelemetry:opentelemetry-sdk-common")
        implementation("io.opentelemetry.semconv:opentelemetry-semconv-incubating")
      }
    }
  }
}

tasks {
  test {
    dependsOn(jar)
    doFirst {
      // use the final jar instead of directories with built classes to test the mrjar functionality
      classpath = jar.get().outputs.files + classpath
    }
    systemProperty("testSecret", "test")
    systemProperty("testPassword", "test")
    systemProperty("testNotRedacted", "test")
  }

  check {
    dependsOn(testing.suites)
  }
}
