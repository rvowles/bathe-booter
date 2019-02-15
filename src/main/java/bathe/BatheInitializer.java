package bathe;

/**
 * This allows packages to implement a service based on this. You should include the bathe booter as a provided
 * resource in your project.
 * *
 * author: Richard Vowles - http://gplus.to/Richard.Vowles
 */
public interface BatheInitializer {
  /**
   * Returns the level of importance of this initializer.
   *
   * @return the lower the value, the more important.
   */
  public int getOrder();

  /**
   * used to allow us to disable this initializer.
   *
   * @return a short name used in a property file
   */
  public String getName();

  /**
   * Called when it is this initializer's turn to run.
   *
   * @param args - the arguments passed to the application (rather than the boioter)
   * @return - the args that will be passed on to the next initializer. This allows initializers to consume arguments
   */
  public String[] initialize(String args[], String jumpClass);
}
