package org.pubref.tools.gradle;

import com.google.common.base.Splitter;

import java.io.File;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.UnsupportedEncodingException;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.google.devtools.common.options.OptionsParser;

import com.google.devtools.build.lib.shell.CommandBuilder;
import com.google.devtools.build.lib.shell.CommandResult;
import com.google.devtools.build.lib.shell.CommandException;

public class GenRequires {

  private static Pattern CONFIG_NAME = Pattern.compile("^([a-zA-Z0-9]+) - .*\\.$");

  private final File file;

  GenRequires(File file) {
    this.file = file;
  }

  public void generate() {
    CommandResult result = null;
    try {

      result = new CommandBuilder()
        .addArg("java")
        .addArg("-Dorg.gradle.appname=gradle")
        .addArg("-classpath /usr/local/Cellar/gradle/2.13/libexec/lib/gradle-launcher-2.13.jar")
        .addArg("org.gradle.launcher.GradleMain")
        .addArg("dependencies")
        //.addArg("--configuration").addArg("runtime")
        .addArg("--build-file").addArg(file.getName())
        .useShell(true)
        .setWorkingDir(file.getParentFile())
        .build()
        .execute();

      if (result.getTerminationStatus().success()) {

        String out = new String(result.getStdout(), "UTF-8");

        GradleDependenciesParser parser = new GradleDependenciesParser();
        GradleDependencies deps = parser.parse(out);

        BazelRequiresWriter writer =
          new BazelRequiresWriter(file.getParentFile(), deps);

        writer.emit();

      } else {
        byte[] bytes = result.getStderr();
        System.err.write(bytes, 0, bytes.length);
      }

    } catch (UnsupportedEncodingException usex) {
      System.err.println("GenDeps failed due to a string encoding error: "
                         + usex.getMessage());
    } catch (IOException ioex) {
      System.err.println("GenDeps failed due to an i/o error: "
                         + ioex.getMessage());
    } catch (CommandException cex) {
      System.err.println("GenDeps failed due to a command execution error: " + cex.getMessage());
      System.err.println("Command: " + cex.getCommand().toDebugString());
      System.err.println("Reason: " + cex.getMessage());
    }

  }

  // ****************************************************************
  // SUPPORT CLASSES
  // ****************************************************************

  static class MavenJar implements Comparable {
    final String group;
    final String name;
    final String version;

    MavenJar(String group, String name, String version) {
      this.group = group;
      this.name = name;
      this.version = version;
    }

    @Override public int compareTo(Object other) {
      MavenJar that = (MavenJar)other;
      return this.getWorkspaceName().compareTo(that.getWorkspaceName());
    }
    public String getArtifact() {
      return group + ':' + name + ":jar:" + version;
    }

    public String getWorkspaceName() {
      return (group + '_' + name).replaceAll("[-.:]", "_");
    }

    public void printRequire(PrintWriter out) {
      printRequire(out, this.getWorkspaceName());
    }

    public void printRequire(PrintWriter out, String name) {
      out.printf("  _require(deps, '%s'),\n", name);
    }

    public void printDep(PrintWriter out) {
      out.printf("  '%s': {\n", this.getWorkspaceName());
      out.printf("    'kind': 'maven_jar',\n");
      out.printf("    'artifact': '%s',\n", this.getArtifact());
      out.printf("  },\n");
    }

    public void printNative(PrintWriter out, String name) {
      out.printf("  if not native.existing_rule('%s'):\n", name);
      out.printf("    native.maven_jar(\n");
      out.printf("      name='%s',\n", name);
      out.printf("      artifact='%s',\n", this.getArtifact());
      out.printf("    )\n");
    }

  }

  static class GradleDependenciesParser {

    protected GradleConfiguration current;
    protected Map<String,GradleConfiguration> configs;
    protected Map<String,MavenJar> jars;

    public GradleDependencies parse(String in) {
      this.current = null;
      this.jars = new HashMap();
      this.configs = new HashMap();

      StreamSupport.stream(
        Splitter.on(System.getProperty("line.separator"))
        .trimResults()
        .omitEmptyStrings()
        .split(in).spliterator(), false)
        .forEach(this::parseLine);

      return new GradleDependencies(this.configs, this.jars);
    }

    private void parseLine(String in) {
      // See if this line represents the beginning of a new
      // configuration section.
      Matcher m = CONFIG_NAME.matcher(in);
      if (m.find()) {
        String name = m.group(1);
        GradleConfiguration config = new GradleConfiguration(name);
        configs.put(name, config);
        this.current = config;
        return;
      }

      int failed = in.indexOf("FAILED");
      if (failed > 0) {
        System.err.println(in);
        return;
      }

      int dashes = in.indexOf("---");
      if (dashes < 0) {
        return;
      }
      in = in.substring(dashes + 4);
      if (in.indexOf("->") > 0) {
        return;
      }
      if (in.endsWith(" (*)")) {
        in = in.substring(0, in.length() - 4);
      }
      if (in.endsWith("---")) {
        return;
      }

      String[] n = in.split(":");
      if (n.length != 3) {
        throw new IllegalArgumentException("Bad maven coordinate: " + in);
      }

      this.addJar(new MavenJar(n[0], n[1], n[2]));
    }

    public void addJar(MavenJar jar) {
      this.jars.put(jar.getWorkspaceName(), jar);
      this.current.addJar(jar);
    }

  }

  static class GradleConfiguration {

    public final String name;
    protected final Map<String,MavenJar> jars;

    GradleConfiguration(String name) {
      this.name = name;
      this.jars = new HashMap();
    }

    public void addJar(MavenJar jar) {
      this.jars.put(jar.getWorkspaceName(), jar);
    }

    public void printDefs(PrintWriter out) {
      out.println();
      out.printf("def %s(deps = DEPS):\n", this.name);
      out.printf("  _require([\n");
      for (MavenJar jar : this.jars.values()) {
        out.printf("    '%s',\n", jar.getWorkspaceName());
      }
      out.printf("  ], deps = deps)\n");
    }

    public void printRequires(PrintWriter out) {
      this.printDefs(out);
    }

    public void printBuild(PrintWriter out) {
      if (jars.isEmpty()) {
        return;
      }

      out.println();
      out.println("java_library(");
      out.printf("  name = '%s',\n", this.name);
      out.println("  licenses = ['notice'],");
      out.println("  exports = [");
      jars.values().stream()
        .map(jar -> jar.getWorkspaceName())
        .sorted()
        .forEach(name -> {
            out.printf("    \"@%s//jar\",\n", name);
          });
      out.println("  ],");
      out.println("  visibility = ['//visibility:public']");
      out.println(")");
    }

    public void printReadme(PrintWriter out) {
      if (jars.isEmpty()) {
        return;
      }

      out.println();
      out.printf("| '%s' | Group | Artifact | Version |\n", this.name);
      out.println("| :--- | ---: | :--- | :--- |");

      jars.values().stream()
        .sorted()
        .forEach(j -> {
            out.printf("| @%s//jar | %s | %s | %s |\n",
                       j.getWorkspaceName(), j.group, j.name, j.version);
          });
    }

  }

  static class GradleDependencies {

    protected final Map<String,GradleConfiguration> configs;
    protected final Map<String,MavenJar> jars;

    GradleDependencies(Map<String,GradleConfiguration> configs,
                       Map<String,MavenJar> jars) {
      this.configs = configs;
      this.jars = jars;
    }

    public void printDeps(PrintWriter out) {
      out.println();
      out.println("DEPS = {");
      for (MavenJar jar : this.jars.values()) {
        jar.printDep(out);
      }
      out.println("}");
    }

    public void printRequires(PrintWriter out) {
      out.printf("# Auto-generated by %s, do not edit:\n", GenRequires.class.getName());
      out.println("load('@org_pubref_rules_require//require:rules.bzl',");
      out.println("     _require = 'require')");
      this.printDeps(out);
      for (GradleConfiguration config : this.configs.values()) {
        config.printRequires(out);
      }
    }

    public void printBuild(PrintWriter out) {
      out.printf("# Auto-generated by %s, do not edit:\n", GenRequires.class.getName());
      for (GradleConfiguration config : this.configs.values()) {
        config.printBuild(out);
      }
    }

    public void printReadme(PrintWriter out) {
      out.printf("# Dependencies of %s:\n", "FILENAME");
      for (GradleConfiguration config : this.configs.values()) {
        config.printReadme(out);
      }
    }

  }

  static class BazelRequiresWriter {

    private final File dir;
    private final GradleDependencies deps;

    BazelRequiresWriter(File dir, GradleDependencies deps) {
      this.dir = dir;
      this.deps = deps;
    }

    public void emit() throws IOException {
      File requires = new File(dir, "requires.bzl");
      try (PrintWriter out = new PrintWriter(new FileWriter(requires))) {
        deps.printRequires(out);
        System.out.format("Generated: %s\n", requires.getAbsolutePath());
      }

      File build = new File(dir, "BUILD");
      try (PrintWriter out = new PrintWriter(new FileWriter(build))) {
        deps.printBuild(out);
        System.out.format("Generated: %s\n", build.getAbsolutePath());
      }

      File readme = new File(dir, "README.md");
      try (PrintWriter out = new PrintWriter(new FileWriter(readme))) {
        deps.printReadme(out);
        System.out.format("Generated: %s\n", readme.getAbsolutePath());
      }

    }

  }

  // ****************************************************************
  // MAIN
  // ****************************************************************

  private static void printUsage(OptionsParser parser) {
    System.out.println("Usage: gen_requires (-g FILE)+ [-o PATH]\n\n"
                       + "Generates a requires.bzl and BUILD file foreach build.gradle "
                       + "file given by -g.\nUnless -o is given, the same dir of "
                       + "the build.gradle file will be used.\n");
    System.out.println(parser.describeOptions(Collections.<String, String>emptyMap(),
                                              OptionsParser.HelpVerbosity.LONG));
  }

  public static void main(String[] args) throws CommandException, UnsupportedEncodingException {

    OptionsParser parser = OptionsParser.newOptionsParser(GenRequiresOptions.class);
    parser.parseAndExitUponError(args);
    GenRequiresOptions options = parser.getOptions(GenRequiresOptions.class);
    if (options.gradleBuildFile.isEmpty()) {
      printUsage(parser);
      return;
    }

    List<File> files = new ArrayList();
    for (String filename : options.gradleBuildFile) {
      File file = new File(filename);
      if (!file.exists()) {
        System.err.println("build.gradle.file does not exist: " + file.getAbsolutePath());
        printUsage(parser);
        return;
      }
      files.add(file);
    }

    for (File file : files) {
      GenRequires r = new GenRequires(file);
      r.generate();
    }

  }

}
