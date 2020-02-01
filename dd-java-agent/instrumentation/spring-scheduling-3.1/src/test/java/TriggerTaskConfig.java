import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class TriggerTaskConfig {
  @Bean
  public TriggerTask triggerTasks() {
    return new TriggerTask();
  }
}
