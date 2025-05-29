/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc.boot;

import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.AbstractUserDetailsAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetails;

public class SavingAuthenticationProvider extends AbstractUserDetailsAuthenticationProvider {

  List<TestUserDetails> latestAuthentications = new ArrayList<>();

  @Override
  protected void additionalAuthenticationChecks(
      UserDetails userDetails, UsernamePasswordAuthenticationToken authentication) {
    // none
  }

  @Override
  protected UserDetails retrieveUser(
      String username, UsernamePasswordAuthenticationToken authentication) {
    TestUserDetails details =
        new TestUserDetails(username, authentication.getCredentials().toString());

    latestAuthentications.add(details);

    return details;
  }
}
