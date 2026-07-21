val stableVersion = "2.30.0"
val alphaVersion = "2.30.0-alpha"

val apidiffBaselineVersion = "2.29.0"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
  extra["apidiffBaselineVersion"] = apidiffBaselineVersion
}
