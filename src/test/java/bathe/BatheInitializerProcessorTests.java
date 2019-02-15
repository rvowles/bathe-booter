package bathe;

import org.junit.Test;

import java.util.Iterator;

import static org.fest.assertions.Assertions.assertThat;

/**
 * author: Richard Vowles - http://gplus.to/Richard.Vowles
 */
public class BatheInitializerProcessorTests {

  @Test
  public void simpleTests() {

    SampleInitializer.value = false;
    SecondSampleInitializer.value = false;

    String[] args = new String[0];

    new BatheInitializerProcessor().process(args, null, this.getClass().getClassLoader());

    assertThat(SampleInitializer.value).isEqualTo(true);
    assertThat(SecondSampleInitializer.value).isEqualTo(false);
  }

  @Test
  public void sortingTests() {
    BatheInitializerProcessor bip = initInitializers();

    assertThat(bip.initializers.size()).isEqualTo(2);
    Iterator<BatheInitializer> it = bip.initializers.iterator();
    assertThat(it.next().getClass()).isEqualTo(SampleInitializer.class);
    assertThat(it.next().getClass()).isEqualTo(SecondSampleInitializer.class);
  }

  private BatheInitializerProcessor initInitializers() {
    BatheInitializerProcessor bip = new BatheInitializerProcessor() {
      @Override
      public void collectInitializers(ClassLoader loader) {
        initializers.add(new SecondSampleInitializer());
        initializers.add(new SampleInitializer());
      }
    };

    bip.process(new String[0], null, null);
    return bip;
  }

  @Test
  public void disableTests() {
    SampleInitializer.value = false;
    SecondSampleInitializer.value = false;

    System.setProperty(BatheInitializerProcessor.BATHE_INITIALIZER_DISABLE + new SampleInitializer().getName(), "spaghetti");

    BatheInitializerProcessor bip = initInitializers();

    assertThat(bip.initializers.size()).isEqualTo(2);
    assertThat(SampleInitializer.value).isEqualTo(false);
  }
}
