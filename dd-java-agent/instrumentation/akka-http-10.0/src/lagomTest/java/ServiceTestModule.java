import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.lightbend.lagom.internal.javadsl.BinderAccessor;
import com.lightbend.lagom.internal.javadsl.server.JavadslServicesRouter;
import com.lightbend.lagom.internal.javadsl.server.ResolvedServices;
import com.lightbend.lagom.internal.javadsl.server.ResolvedServicesProvider;
import com.lightbend.lagom.internal.javadsl.server.ServiceInfoProvider;
import com.lightbend.lagom.internal.server.status.MetricsServiceImpl;
import com.lightbend.lagom.javadsl.api.ServiceInfo;
import com.lightbend.lagom.javadsl.server.LagomServiceRouter;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import com.lightbend.lagom.javadsl.server.status.MetricsService;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServiceTestModule extends AbstractModule implements ServiceGuiceSupport {
  public static final ExecutorService executor = Executors.newCachedThreadPool();

  @Override
  protected void configure() {
    bindServices(
        serviceBinding(EchoService.class, EchoServiceImpl.class)
        // , serviceBinding(HelloService.class, HelloServiceImpl.class)
        );
  }

  // ------------------------------

  /**
   * This is a copy of {@link
   * com.lightbend.lagom.javadsl.server.ServiceGuiceSupport#bindServices(ServiceGuiceSupport.ServiceBinding[])}
   * that should survive deprecation. When removing the method from the superclass this should
   * inherit the removed code.
   *
   * <p>This method is used in docs/ so that many tests can share a single Guice module.
   */
  @Override
  public void bindServices(ServiceBinding<?>... serviceBindings) {
    Binder binder = BinderAccessor.binder(this);

    for (ServiceBinding binding : serviceBindings) {
      // First, bind the client implementation.  A service should be able to be a client to itself.
      bindClient(binding.serviceInterface());

      // Now, bind the server implementation to itself as an eager singleton.
      if (binding instanceof ClassServiceBinding) {
        binder.bind(((ClassServiceBinding<?>) binding).serviceImplementation()).asEagerSingleton();
      } else {
        Object service = ((InstanceServiceBinding<?>) binding).service();
        binder.bind((Class<Object>) service.getClass()).toInstance(service);
      }
    }

    ServiceBinding<?> primaryServiceBinding = serviceBindings[0];
    // Bind the service info for the first one passed in
    binder
        .bind(ServiceInfo.class)
        .toProvider(
            new ServiceInfoProvider(
                primaryServiceBinding.serviceInterface(),
                Arrays.stream(serviceBindings)
                    .map(ServiceBinding::serviceInterface)
                    .toArray(Class[]::new)));

    // Bind the metrics
    ServiceBinding<MetricsService> metricsServiceBinding =
        serviceBinding(MetricsService.class, MetricsServiceImpl.class);
    binder
        .bind(((ClassServiceBinding<?>) metricsServiceBinding).serviceImplementation())
        .asEagerSingleton();
    ServiceBinding<?>[] allServiceBindings = new ServiceBinding<?>[serviceBindings.length + 1];
    System.arraycopy(serviceBindings, 0, allServiceBindings, 0, serviceBindings.length);
    allServiceBindings[allServiceBindings.length - 1] = metricsServiceBinding;

    // Bind the resolved services
    binder
        .bind(ResolvedServices.class)
        .toProvider(new ResolvedServicesProvider(allServiceBindings));

    // And bind the router
    binder.bind(LagomServiceRouter.class).to(JavadslServicesRouter.class);
  }
}
