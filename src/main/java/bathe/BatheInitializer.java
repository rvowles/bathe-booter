package bathe;

/**
 * This allows packages to implement a service based on this. You should include the bathe booter as a provided
 * resource in your project.
 *
 * author: Richard Vowles - http://gplus.to/Richard.Vowles
 */
public interface BatheInitializer {
	public void initialize(String args[]);
}
