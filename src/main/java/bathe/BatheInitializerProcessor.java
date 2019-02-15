package bathe;

import java.util.Comparator;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;

/**
 * Collects all the initializers as services and runs them in the order they are specified, allowing them
 * to be skipped. This is done before jumping to the specified jump-class.
 * <p>
 * author: Richard Vowles - http://gplus.to/Richard.Vowles
 */
public class BatheInitializerProcessor {
  public static final String BATHE_INITIALIZER_DISABLE = "bathe.disable.";

  protected Set<BatheInitializer> initializers;

  public BatheInitializerProcessor() {
    initializers = new TreeSet<>(new Comparator<BatheInitializer>() {
      @Override
      public int compare(BatheInitializer o1, BatheInitializer o2) {
        int orderCompare = Integer.compare(o1.getOrder(), o2.getOrder());

        if (orderCompare == 0) {
          orderCompare = o1.getName().compareTo(o2.getName());
        }

        return orderCompare;
      }
    });
  }

  protected void collectInitializers(ClassLoader loader) {
    ServiceLoader<BatheInitializer> services = ServiceLoader.load(BatheInitializer.class, loader);

    for (BatheInitializer initializer : services) {
      initializers.add(initializer);
    }
  }

  //  Run any initializers.
  public String[] process(String[] args, String jumpClass, ClassLoader loader) {
    collectInitializers(loader == null ? Thread.currentThread().getContextClassLoader() : loader);

    for (BatheInitializer initializer : initializers) {
      if (System.getProperty(BATHE_INITIALIZER_DISABLE + initializer.getName()) == null) {
        args = initializer.initialize(args, jumpClass);
      }
    }

    return args;
  }
}
