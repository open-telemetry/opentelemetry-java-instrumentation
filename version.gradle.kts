allprojects {
  if (findProperty("otel.stable") != "true") {
    version = "1.8.0-alpha-SNAPSHOT"
  } else {
    version = "1.8.0-SNAPSHOT"
  }
}
