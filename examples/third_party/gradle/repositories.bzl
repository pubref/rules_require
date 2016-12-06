GRADLE_BUILD_FILE = """
java_import(
  name = "launcher",
  jars = ["lib/gradle-launcher.jar"],
)
"""

def gradle_repositories():
    if not native.existing_rule("gradle"):
        native.new_http_archive(
            name = "gradle",
            url = "https://services.gradle.org/distributions/gradle-3.2.1-bin.zip",
            build_file_content = GRADLE_BUILD_FILE,
        )
