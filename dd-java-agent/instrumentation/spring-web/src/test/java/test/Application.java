package test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@SpringBootApplication
public class Application {
  public static final String USER = "username";
  public static final String PASS = "password";
  private static final String ROLE = "USER";

  public static void main(final String[] args) {
    SpringApplication.run(Application.class, args);
  }

  @Configuration
  @EnableWebSecurity
  public class WebSecurityConfig extends WebSecurityConfigurerAdapter {
    @Override
    protected void configure(final HttpSecurity http) throws Exception {
      http.csrf().disable().authorizeRequests().anyRequest().authenticated().and().httpBasic();
    }

    @Bean
    @Override
    public UserDetailsService userDetailsService() {
      return new InMemoryUserDetailsManager(
          User.withUsername(USER).password(PASS).roles(ROLE).build());
    }
  }
}
