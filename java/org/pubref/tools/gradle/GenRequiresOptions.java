package org.pubref.tools.gradle;

import com.google.devtools.common.options.Option;
import com.google.devtools.common.options.OptionsBase;

import java.util.List;

/**
 * Command-line options for gen_requires tool.
 */
public class GenRequiresOptions extends OptionsBase {

  @Option(
      name = "help",
      abbrev = 'h',
      help = "Prints usage info.",
      defaultValue = "true"
  )
  public boolean help;

  @Option(
      name = "build_gradle_file",
      abbrev = 'g',
      help = "Path to build.gradle file.",
      allowMultiple = true,
      category = "input",
      defaultValue = ""
  )
  public List<String> gradleBuildFile;

  @Option(
      name = "output_dir",
      abbrev = 'o',
      help = "Output directory to store the requires.bzl and BUILD files.",
      category = "output",
      defaultValue = ""
  )
  public String outputDir;

}
