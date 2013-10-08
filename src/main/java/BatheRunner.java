

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
 * author: Richard Vowles - http://gplus.to/RichardVowles
 *
 * Thanks to Karsten Sperling for the idea of embedding the jars into subdirectories and loading them into the URL classpath from there.
 */
public class BatheRunner {
  private static final String WEB_JAR_PREFIX = "WEB-INF/jars/";
  private static final String WEB_CLASSES_PREFIX = "WEB-INF/classes/";

  private static final String MINUS_D = "-D";
  private static final String MINUS_P = "-P";
  private static final String MAIN_OVERRIDE = "-R";
  private static final String JUMP_CLASS = "Jump-Class";
  protected static final String BATHE_INITIALIZER = "bathe.initializers";
  protected static final String BATHE_JAR_ORDER_OVERRIDE = "bathe.jarOrderOverride";

  protected String runnerClass;
  protected File jar;
  protected String[] passingArgs;
  protected boolean foundClassDir = false;

  protected void parseCommandLine(String[] args) {
    List<String> appArguments = new ArrayList<String>();

    // Process command line arguments
    for (String arg : args) {
      if (arg.startsWith(MINUS_D)) {
        String property = arg.substring(MINUS_D.length());
        int equals = property.indexOf('=');
        if (equals >= 0)
          System.setProperty(property.substring(0, equals), property.substring(equals + 1));
        else
          System.setProperty(property, Boolean.TRUE.toString());
      } else if (arg.startsWith(MINUS_P)) {
        File properties = new File(arg.substring(MINUS_P.length()));
        System.getProperties().putAll(loadProperties(properties, false));
      } else if (arg.startsWith(MAIN_OVERRIDE)) {
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
          "  " + MINUS_D + "<name>=<value>    set system property\n" +
          "  " + MINUS_P + "<file>            load the properties file into system properties (no duplicates)\n" +
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
          break;
        }
      }
    } catch (IOException iex) {

    }

  }

  public static void main(String []args) throws IOException {
    new BatheRunner().run(args);
  }

  public void run(String []args) throws IOException {
    // Find the WAR and load the corresponding properties
    jar = getRunFile();

    parseCommandLine(args);

    List<String> jarOffsets = determineJarOffsets(jar);
    URLClassLoader loader = createUrlClassLoader(jar, jarOffsets);

    Thread.currentThread().setContextClassLoader(loader);

    try {
      checkForInitializers(loader);

      // Start the application
      exec(loader, jar, runnerClass, passingArgs);
    } finally {
      Thread.currentThread().setContextClassLoader(null);

      loader.close(); // supported in 1.7
    }
  }

  //  Run any initializers. (e.g. liquibase)
  protected void checkForInitializers(ClassLoader loader) {
    String initializers = System.getProperty(BATHE_INITIALIZER);

    System.out.println("Initializer is " + initializers);

    if (initializers != null) {
      String inits[] = initializers.trim().split(",");

      for (String className : inits) {
        try {
          Class clazz = Class.forName(className, true, loader);
          Object i = clazz.newInstance();
          Method m = getMethod(i, clazz, "init");
          m.invoke(i);
        } catch (Exception ex) {
          throw new RuntimeException(String.format("Failed to initialize due to missing class '%s'", className), ex);
        }
      }
    }
  }

  protected Properties loadProperties(File file, boolean optional) {
    Properties values = new DuplicateProperties();
    if (!optional || file.exists()) {
      try {
        InputStream is = new FileInputStream(file);
        try {
          values.load(is);
        } finally {
          is.close();
        }
      } catch (IOException e) {
        throw new RuntimeException(String.format("Failed to read properties file '%s'", file), e);
      }
    }
    return values;
  }

  /**
   * Runs the main runner class in the specified class loader, passing in
   * the WAR being run as well as the specified command line arguments.
   */
  protected void exec(ClassLoader loader, File runnable, String runnerClass, String[] args) {
    try {
      Class<?> runner = Class.forName(runnerClass, true, loader);

      runner.getMethod("run", File.class, String[].class).invoke(null, runnable, args);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("The class you are trying to run can not be found on the classpath: " + runnerClass, e);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException("Can't find the run method in the run class", e);
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

      String jarPrefix = "jar:" + war.toURI().toString() + "!/";

      if (foundClassDir)
        urls.add(new URL(jarPrefix + WEB_CLASSES_PREFIX));

      for (String jar : libraries) {
        URL newLibraryOffset = new URL(jarPrefix + WEB_JAR_PREFIX + jar + "/");

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

      determineSorting(jars);

      return Collections.unmodifiableList(jars);
    } catch (IOException e) {
      throw new RuntimeException("Unable to determine the extracted jar files in the jar/war", e);
    }
  }

  public void determineSorting(List<String> jars) {
    String jarOrderOverride = System.getProperty(BATHE_JAR_ORDER_OVERRIDE);

    if (jarOrderOverride == null) return;

    final List<String> order = new ArrayList<>();
    for(String orderingElement : jarOrderOverride.trim().split((","))) {
      order.add(orderingElement.trim());
    }

    Collections.sort(jars, new Comparator<String>() {
      @Override
      public int compare(String s1, String s2) {

        int s1Pos = findPartial(s1, order);
        int s2Pos = findPartial(s2, order);

        if (s1Pos < s2Pos) return -1;
        if (s1Pos == s2Pos) return 0;
        if (s1Pos > s2Pos) return 1;

        return 0;
      }
    });
  }

  public int findPartial(String jarName, List<String> order) {
    for(int count = 0, max = order.size(); count < max; count ++) {
//      System.out.print(jarName + " contains " + order.get(count) + ": ");
      if (jarName.contains(order.get(count))) {
        int val = ((order.size() - count) * -1) - 1;
//        System.out.println("yes " + order.get(count) + "/" + jarName + " = " + val);
        return val;
      }
//      System.out.print("no, ");
    }

//    System.out.println("no match " + jarName + " 0");

    return 0;
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

  protected  Method getMethod(Object object, Class<?> clazz, String name, Class<?>... params) throws NoSuchMethodException {
    if (params.length > 0)
      return clazz.getMethod(name, params);
    else
      return clazz.getMethod(name);
  }

  /**
   * Ensure that two are not the same
   */

  protected static final class DuplicateProperties extends Properties {

    @Override
    public synchronized Object put(Object key, Object value) {
      Object previous = super.put(key, value);

      if (previous != null) {
        throw new IllegalStateException(String.format("Key '%s' has duplicate values as %s and %s", key, previous.toString(), value.toString()));
      }

      return null;
    }
  }
}
