plugins {
  id("otel.library-instrumentation")
}

val mrJarVersions = listOf(9, 11)

dependencies {
  implementation("io.opentelemetry:opentelemetry-sdk-common")
  implementation("io.opentelemetry:opentelemetry-semconv")
  implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")

  annotationProcessor("com.google.auto.service:auto-service")
  compileOnly("com.google.auto.service:auto-service-annotations")
  testCompileOnly("com.google.auto.service:auto-service-annotations")

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
    for (version in mrJarVersions) {
      into("META-INF/versions/$version") {
        from(sourceSets["java$version"].output)
      }
    }
    manifest.attributes(
      "Multi-Release" to "true"
    )
  }
}
