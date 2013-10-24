package bathe;

import java.util.*;

/**
 * Collects all the initializers as services and runs them in the order they are specified, allowing them
 * to be skipped. This is done before jumping to the specified jump-class.
 *
 * author: Richard Vowles - http://gplus.to/Richard.Vowles
 */
public class BatheInitializerProcessor {
	public static final String BATHE_INITIALIZER_DISABLE = "bathe.disable.";

	protected Set<BatheInitializer> initializers;

	protected void collectInitializers(ClassLoader loader) {
		ServiceLoader<BatheInitializer> services = ServiceLoader.load(BatheInitializer.class, loader);

		for(BatheInitializer initializer: services) {
			initializers.add(initializer);
		}
	}

	public BatheInitializerProcessor() {
		initializers = new TreeSet<>(new Comparator<BatheInitializer>() {
			@Override
			public int compare(BatheInitializer o1, BatheInitializer o2) {
				return Integer.compare(o1.getOrder(), o2.getOrder());
			}
		});
	}

	//  Run any initializers.
	public void process(String[] args, ClassLoader loader) {
		collectInitializers(loader);

		for(BatheInitializer initializer: initializers) {
			if (System.getProperty(BATHE_INITIALIZER_DISABLE + initializer.getName()) == null) {
				initializer.initialize(args);
			}
		}
	}
}
