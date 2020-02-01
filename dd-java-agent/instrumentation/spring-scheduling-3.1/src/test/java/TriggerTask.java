import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Component
public class TriggerTask implements Runnable {

  private final CountDownLatch latch = new CountDownLatch(1);

  @Scheduled(cron = "0/5 * * * * *")
  @Override
  public void run() {
    latch.countDown();
  }

  public void blockUntilExecute() throws InterruptedException {
    latch.await(5, TimeUnit.SECONDS);
  }
}
