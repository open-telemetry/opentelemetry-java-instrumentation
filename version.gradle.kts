val stableVersion = "1.31.0"
val alphaVersion = "1.31.0-alpha"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
