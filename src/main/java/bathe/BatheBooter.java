package bathe;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;


/*
 * This class expects SpringLoader to have loaded the classpath.
 *
 * loads any BatheInitializer services in the right order according to precedence and then jumps in.
 *
 * author: Richard Vowles - http://gplus.to/RichardVowles
 */
public class BatheBooter {
  private static final String WEB_JAR_PREFIX = "WEB-INF/jars/";
  private static final String WEB_CLASSES_PREFIX = "WEB-INF/classes/";

  private static final String MAIN_OVERRIDE = "-R";
  private static final String JUMP_CLASS = "Jump-Class";
  protected static final String BATHE_EXTERNAL_CLASSPATH = "bathe.externalClassPath";
	private static final String BATHE_IMPLEMENTATION_VERSION = "Bathe-Implementation-Version";

	protected String runnerClass;
  protected File jar;
  protected String[] passingArgs;

  protected void parseCommandLine(String[] args) {
    List<String> appArguments = new ArrayList<String>();

    // Process command line arguments
    for (String arg : args) {
      if (arg.startsWith(MAIN_OVERRIDE)) {
        runnerClass = arg.substring(MAIN_OVERRIDE.length());
      } else
        appArguments.add(arg);
    }

    if (runnerClass == null) {
      attemptToGetJumpClassFromManifest();
    }

    if (appArguments.size() > 0 && runnerClass == null) {
      System.err.println(
        "Usage: java -jar " + jar.getName() + " [options]\n" +
          "\n" +
          "Arguments:\n" +
          "  " + MAIN_OVERRIDE + "<class-name>      specify the main class.\n" +
          "  \n" +
          "Specify Jump-Class: in your /META-INF/MANIFEST.MF to automatically define a class to run");

      System.exit(-2);
    }


    passingArgs = new String[appArguments.size()];

    appArguments.toArray(passingArgs);
  }

  protected void attemptToGetJumpClassFromManifest() {
    try {
      Enumeration<URL> resources = Thread.currentThread().getContextClassLoader()
        .getResources("META-INF/MANIFEST.MF");

      while (resources.hasMoreElements()) {
        Manifest manifest = new Manifest(resources.nextElement().openStream());

        Attributes attr = manifest.getMainAttributes();
        String jumpGate = attr.getValue(JUMP_CLASS);

        if (jumpGate != null) {

          runnerClass = jumpGate;

	        System.setProperty(BATHE_IMPLEMENTATION_VERSION, attr.getValue(Attributes.Name.IMPLEMENTATION_VERSION));

          break;
        }
      }

    } catch (IOException iex) {

    }

  }

  public static void main(String []args) throws IOException {
    new BatheBooter().run(args);
  }

  public void run(String []args) throws IOException {
    // Find the WAR and load the corresponding properties
    jar = getRunFile();

    parseCommandLine(args);

	  String externalClasspath = System.getProperty(BATHE_EXTERNAL_CLASSPATH);

	  // support external classpath
	  ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

	  if (externalClasspath != null) {
		  classLoader = new URLClassLoader(new URL[] {new File(externalClasspath).toURI().toURL()}, classLoader);
	  }

    runWithLoader(classLoader, jar, runnerClass, passingArgs);
  }

	public void runWithLoader(ClassLoader loader, File runnable, String runnerClass, String[] args) throws IOException {
		ClassLoader localLoader = loader == null ? Thread.currentThread().getContextClassLoader() : loader;

		try {
			args = new BatheInitializerProcessor().process(args, runnerClass, localLoader);

			// Start the application
			exec(localLoader, runnable, runnerClass, args);
		} finally {
			Thread.currentThread().setContextClassLoader(null);
		}
	}


	private boolean tryRunMethod(Class<?> runner, File runnable, String[] args) throws InvocationTargetException, IllegalAccessException {
		try {
			Method method = runner.getMethod("run", File.class, String[].class);

			method.invoke(null, runnable, args);
		} catch (NoSuchMethodException e) {
			return false;
		}

		return true;
	}

	private boolean tryMainMethod(Class<?> runner, String[] args) throws InvocationTargetException, IllegalAccessException {
		try {
			Method method = runner.getMethod("main", String[].class);

			// force to Object so it doesn't  try to use ...
			method.invoke(null, (Object)args);
		} catch (NoSuchMethodException e) {
			return false;
		}

		return true;
	}

	/*
   * Runs the main runner class in the specified class loader, passing in
   * the WAR being run as well as the specified command line arguments.
   */
  public void exec(ClassLoader loader, File runnable, String runnerClass, String[] args) {
    try {
      Class<?> runner = Class.forName(runnerClass, true, loader);

	    if (!tryRunMethod(runner, runnable, args)) {
		    if (!tryMainMethod(runner, args)) {
			    throw new RuntimeException("Cannot find run or main method in " + runnerClass);
		    }
	    }

    } catch (ClassNotFoundException e) {
      throw new RuntimeException("The class you are trying to run can not be found on the classpath: " + runnerClass + ", " + loader.toString(), e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Run method needs to be public and static", e);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException) throw (RuntimeException) cause;
      throw new RuntimeException("Unhandled exception in WebApp runner", e);
    }
  }

  /*
   * This may end up needing to be more sophisticated if there is more than one jar on the command line. We would need to looking through
   * for the one with this class in it.
   *
   * @return The main file we are running
   */
  protected File getRunFile() {
    for (URL url : getSystemClassPath()) {
      if ("file".equals(url.getProtocol()) && (url.getPath().endsWith(".war") || url.getPath().endsWith(".jar"))) {
        try {
          return new File(url.toURI());
        } catch (URISyntaxException e) {
          /* ignored */
        }
      }
    }
    throw new RuntimeException("Cannot get the runnable artifact");
  }

  /*
   * Returns the URLs that make up the system class path.
   */
  protected URL[] getSystemClassPath() {
    ClassLoader loader = ClassLoader.getSystemClassLoader();
    if (!(loader instanceof URLClassLoader))
      throw new RuntimeException("System class loader does not expose classpath URLs");
    return ((URLClassLoader) loader).getURLs();
  }


}
