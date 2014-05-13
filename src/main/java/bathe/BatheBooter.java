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


/**
 * This class takes the jar/war in the classpath, figures out what the classpath is, creates a new URL class loader,
 * loads any BatheInitializer services in the right order according to precedence and then jumps in.
 *
 * author: Richard Vowles - http://gplus.to/RichardVowles
 *
 * Thanks to Karsten Sperling for the idea of embedding the jars into subdirectories and loading them into the URL classpath from there.
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
  protected boolean foundClassDir = false;

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

    List<String> jarOffsets = determineJarOffsets(jar);
    URLClassLoader loader = createUrlClassLoader(jar, jarOffsets);

    Thread.currentThread().setContextClassLoader(loader);

    runWithLoader(loader, jar, runnerClass, passingArgs);

	  loader.close(); // supported in 1.7
  }

	public void runWithLoader(URLClassLoader loader, File runnable, String runnerClass, String[] args) throws IOException {
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

	/**
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

  /**
   * Creates a URL class loader out of the specified library offsets within the war file
   */
  protected URLClassLoader createUrlClassLoader(File war, List<String> libraries) {
    List<URL> urls;

    try {
      urls = new ArrayList<URL>();

	    // external classpaths first
	    String externalClasspath = System.getProperty(BATHE_EXTERNAL_CLASSPATH);
	    if (externalClasspath != null) {
		    for(String cp : externalClasspath.split(",")) {
			    urls.add(new URL(cp.trim()));
		    }
	    }

	    // now internal classpaths
	    String jarPrefix = "jar:" + war.toURI().toString() + "!/";

      if (foundClassDir)
        urls.add(new URL(jarPrefix + WEB_CLASSES_PREFIX));

      for (String jar : libraries) {
	      // the trailing / is extremely important, if it isn't there, it treats it as a JarLoader and fails
	      // to load it as it changes the url to jar:jar:file:!/blah!/
        URL newLibraryOffset = new URL(jarPrefix + WEB_JAR_PREFIX + jar + "/" );

        urls.add(newLibraryOffset);
      }

    } catch (MalformedURLException e) {
      throw new RuntimeException("Unable to create JAR/WAR class path", e);
    }

    URL[] cp = new URL[urls.size()];
    return new URLClassLoader(urls.toArray(cp), ClassLoader.getSystemClassLoader());
  }

  /**
   *
   * Extracts out the jars that are embedded in subdirectories and sets them up in a list to allow priority sorting and
   * construction of a URLClassPath.
   *
   */
  protected List<String> determineJarOffsets(File war) {
    try {
      List<String> jars = new ArrayList<>();

      JarFile archive = new JarFile(war);

      int webJarLength = WEB_JAR_PREFIX.length();

      try {
        Enumeration<JarEntry> i = archive.entries();

        while (i.hasMoreElements()) {
          JarEntry entry = i.nextElement();
          String name = entry.getName();

          if (entry.isDirectory()) {
            //stop problem where the WEB_JAR_PREFIX/WEB_CLASSES_PREFIX is included as an entry
            if (name.equals(WEB_JAR_PREFIX) || name.equals(WEB_CLASSES_PREFIX)) continue;

            if (name.startsWith(WEB_JAR_PREFIX)) {
              String partName = name.substring(webJarLength);

              String jarDir = partName.substring(0, partName.indexOf('/'));

              if (!jars.contains(jarDir))
                jars.add(jarDir);
            } else if (name.startsWith(WEB_CLASSES_PREFIX)) {
              foundClassDir = true;
            }
          }
        }
      } finally {
        archive.close();
      }
      return Collections.unmodifiableList(jars);
    } catch (IOException e) {
      throw new RuntimeException("Unable to determine the extracted jar files in the jar/war", e);
    }
  }

  /**
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

  /**
   * Returns the URLs that make up the system class path.
   */
  protected URL[] getSystemClassPath() {
    ClassLoader loader = ClassLoader.getSystemClassLoader();
    if (!(loader instanceof URLClassLoader))
      throw new RuntimeException("System class loader does not expose classpath URLs");
    return ((URLClassLoader) loader).getURLs();
  }


}
