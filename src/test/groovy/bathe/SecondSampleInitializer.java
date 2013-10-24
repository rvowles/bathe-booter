package bathe;

/**
 * author: Richard Vowles - http://gplus.to/Richard.Vowles
 */
public class SecondSampleInitializer implements BatheInitializer {

	public static boolean value = false;

	@Override
	public int getOrder() {
		return 99;
	}

	@Override
	public String getName() {
		return "simple2";
	}

	@Override
	public void initialize(String[] args) {
		value = true;
	}
}
