package bathe;

import org.junit.Test;

import static org.fest.assertions.Assertions.*;

/**
 * author: Richard Vowles - http://gplus.to/RichardVowles
 */
public class BatheRunnerTest {
  @Test
  public void wibble() {

  }
//  @Before
//  public void initial() {
//    System.clearProperty(BatheBooter.BATHE_JAR_ORDER_OVERRIDE);
//  }
//
//  @Test
//  public void ensureClasspathIsSortedWithPatchesUpFront() {
//    System.setProperty(BatheBooter.BATHE_JAR_ORDER_OVERRIDE, "my-patch, your-patch, his-patch");
//
//    List<String> files = Arrays.asList("your-patch", "logback-1.2.3", "his-56-patch", "slf4j-1-7.1", "his-patch-778", "groovy-2.1.10", "servlet-3", "my-patch-1.56");
//
//    new BatheBooter().determineSorting(files);
//
//    assertThat(files.get(0)).isEqualTo("my-patch-1.56");
//    assertThat(files.get(1)).isEqualTo("your-patch");
//    assertThat(files.get(2)).isEqualTo("his-patch-778");
//  }
//
//  @Test
//  public void parameterParsing() {
//    BatheBooter batheRunner = new BatheBooter() {
//      @Override
//      protected void attemptToGetJumpClassFromManifest() {
//        this.runnerClass = "woopsie";
//      }
//    };
//
//    batheRunner.parseCommandLine(new String[]{});
//
//    assertThat(batheRunner.runnerClass).isEqualTo("woopsie");
//    assertThat(batheRunner.passingArgs).isNotNull();
//    assertThat(batheRunner.passingArgs).isEmpty();
//
//    batheRunner.parseCommandLine(new String[]{"-Rwibble", "One", "Two", "Three"});
//
//    assertThat(batheRunner.runnerClass).isEqualTo("wibble");
//    assertThat(batheRunner.passingArgs).isNotNull();
//    assertThat(batheRunner.passingArgs.length).isEqualTo(3);
//    assertThat(batheRunner.passingArgs[0]).isEqualTo("One");
//    assertThat(batheRunner.passingArgs[1]).isEqualTo("Two");
//    assertThat(batheRunner.passingArgs[2]).isEqualTo("Three");
//
//    batheRunner.parseCommandLine(new String[]{"-Dpp1.parm1=4"});
//    assertThat(System.getProperty("pp1.parm1")).isEqualTo("4");
//
//    batheRunner.parseCommandLine(new String[]{"-Psrc/test/resources/test.properties"});
//    assertThat(System.getProperty("chunky.biscuits")).isEqualTo("3");
//
//    try {
//      batheRunner.parseCommandLine(new String[]{"-Psrc/test/resources/dupe.properties"});
//      assertThat(false).isEqualTo(true);
//    } catch (IllegalStateException iex) {
//
//    }
//  }
}
