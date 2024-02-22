# Settings for the OSHI instrumentation

| System property                                           | Type    | Default | Description              |
|-----------------------------------------------------------| ------- | ------- |--------------------------|
| `otel.instrumentation.oshi.experimental-metrics.enabled`  | Boolean | `false` | Enable the OSHI metrics. |

# Using OSHI with OpenTelemetry Java agent

Step1: Downloading OSHI Jar: 

You can download the OSHI jar file from the official Maven repository 
https://search.maven.org/search?q=g:com.github.oshi%20AND%20a:oshi-core


Step2: Adding OSHI Jar to Classpath:

Once you've downloaded the OSHI jar file, you need to add it to the classpath of your Java application.
Agent loads oshi classes from system class loader so oshi-core jar should be placed on the class path.


Conclusion: By following the steps mentioned above, you can manually download the OSHI jar file and add it to the system classpath for your Java applications, enabling access to system hardware information and monitoring capabilities provided by OSHI.



