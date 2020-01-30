import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class IntervalTaskConfig {
  @Bean
  public IntervalTask scheduledTasks() {
    return new IntervalTask();
  }
}
