import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ScheduledTasksConfig {
  @Bean
  public ScheduledTasks scheduledTasks() {
    return new ScheduledTasks();
  }
}
