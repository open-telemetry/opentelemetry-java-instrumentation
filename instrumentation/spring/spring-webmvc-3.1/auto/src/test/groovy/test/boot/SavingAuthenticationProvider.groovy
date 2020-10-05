/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.boot

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.authentication.dao.AbstractUserDetailsAuthenticationProvider
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

class SavingAuthenticationProvider extends AbstractUserDetailsAuthenticationProvider {
  List<TestUserDetails> latestAuthentications = new ArrayList<>()

  @Override
  protected void additionalAuthenticationChecks(UserDetails userDetails, UsernamePasswordAuthenticationToken authentication) throws AuthenticationException {
    // none
  }

  @Override
  protected UserDetails retrieveUser(String username, UsernamePasswordAuthenticationToken authentication) throws AuthenticationException {
    def details = new TestUserDetails(username, authentication.credentials.toString())

    latestAuthentications.add(details)

    return details
  }
}

class TestUserDetails implements UserDetails {
  private final String username
  private final String password

  TestUserDetails(String username, String password) {
    this.username = username
    this.password = password
  }

  @Override
  Collection<? extends GrantedAuthority> getAuthorities() {
    return Collections.emptySet()
  }

  @Override
  String getPassword() {
    return password
  }

  @Override
  String getUsername() {
    return username
  }

  @Override
  boolean isAccountNonExpired() {
    return true
  }

  @Override
  boolean isAccountNonLocked() {
    return true
  }

  @Override
  boolean isCredentialsNonExpired() {
    return true
  }

  @Override
  boolean isEnabled() {
    return true
  }
}
