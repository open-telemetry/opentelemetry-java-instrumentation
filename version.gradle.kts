val snapshot = true

allprojects {
  var ver = "1.13.0"
  if (findProperty("otel.stable") != "true") {
    ver += "-alpha"
  }
  if (snapshot) {
    ver += "-SNAPSHOT"
  }
  version = ver
}
