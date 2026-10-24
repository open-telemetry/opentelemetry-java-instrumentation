/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.repro.lettuce;

import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReproApplication {

  public static void main(String[] args) throws Exception {
    if (args.length > 0 && args[0].equals("load")) {
      // the load generator is packaged in the same jar, but runs in a separate JVM without agent
      LoadGenerator.main(Arrays.copyOfRange(args, 1, args.length));
      return;
    }
    SpringApplication.run(ReproApplication.class, args);
  }
}
