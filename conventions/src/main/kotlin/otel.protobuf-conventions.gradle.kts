import com.google.protobuf.gradle.*

plugins {
  id("com.google.protobuf")

  id("otel.java-conventions")
}

protobuf {
  protoc {
    // The artifact spec for the Protobuf Compiler
    artifact = "com.google.protobuf:protoc:3.3.0"
  }
  plugins {
    id("grpc") {
      artifact = "io.grpc:protoc-gen-grpc-java:1.6.0"
    }
  }
  generateProtoTasks {
    all().configureEach {
      plugins {
        id("grpc")
      }
    }
  }
}

afterEvaluate {
  // Classpath when compiling protos, we add dependency management directly
  // since it doesn't follow Gradle conventions of naming / properties.
  dependencies {
    add("compileProtoPath", platform(project(":dependencyManagement")))
    add("testCompileProtoPath", platform(project(":dependencyManagement")))
  }
}
