/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
