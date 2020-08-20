## Rationale for not supporting Java 7

### Android support is no longer tied to Java 7

Even for supporting old Android API levels:

> If you're building your app using Android Gradle plugin 4.0.0 or higher, the plugin extends
> support for using a number of Java 8 language APIs <b>without requiring a minimum API level for
> your app</b>.

(https://developer.android.com/studio/write/java8-support#library-desugaring)

There are some Java 8 APIs that Android does not desugar, but we can use
[animal sniffer plugin](https://github.com/xvik/gradle-animalsniffer-plugin) to ensure we don't use
those particular Java 8 APIs that are not available in the base Android level we decide to support,
e.g. OkHttp takes this approach to
[ensure compliance with Android API level 21](https://github.com/square/okhttp/blob/96a2118dd447ebc28a64d9b11a431ca642edc441/build.gradle#L144-L153)

We will use this approach for the `instrumentation-api` module and for any library instrumentation
that would be useful to Android developers.

### Modern test tooling requires Java 8+

Both JUnit 5 and Testcontainers both require Java 8+.

### Auto-instrumentation (Javaagent)

We could run tests against Java 8+ and ensure Java 7 compliance by using similar animal sniffer
technique as above.

But bytecode instrumentation tends to be much more sensitive to Java versions than normal code, and
we would lose a lot of confidence in the quality of our Java 7 support without being able to run our
standard tests against it.

### Library (manual) instrumentation

We believe that Java 7 users are primarily in maintenance mode and not interested in cracking open
their code anymore and adding library (manual) instrumentation, so we don't believe there is much
interest in library instrumentation targeting Java 7.
