# `rules_require` [![Build Status](https://travis-ci.org/pubref/rules_require.svg?branch=master)](https://travis-ci.org/pubref/rules_require)

[Bazel][bazel-home] uses a flat dependency model that does not support
transitive dependencies.  It is an error to attempt to redefine an
external workspace.

This repository provides the following:

1. A `require` rule that will check if an
   existing rule exists before attempting to redefine it.

2. A tool to generate `require` templates from gradle.

### Rules

| Name                     | Description |
| -------------------: | -----------: | --------: | -------- |
| [require_repositories](#require_repositories)  | Load dependencies for this repo. |
| [require](#require)  | Require a single dependency. |

# Usage

## 1. Add rules_require your WORKSPACE

```python
git_repository(
  name = "org_pubref_rules_require",
  remote = "https://github.com/pubref/rules_require",
  tag = "v0.1.0",
)

load("@org_pubref_rules_require//require:rules.bzl", "require_repositories")
require_repositories()
```

## 2. Generate dependencies from gradle.

The convention is to have one minimal `build.gradle` file per
subdirectory in `third_party`.  For example, consider the following
example `build.gradle` file in `third_party/aether`:

```sh
third_party/aether/
└── build.gradle
```

```groovy
apply plugin: 'java'
repositories {
  mavenCentral()
}
dependencies {
  compile group: 'org.eclipse.aether', name: 'aether-spi', version: '1.1.0'
  ...
}
```

The following `gendeps` tool invocation will invoke gradle, collect
the dependency tree, and generate three files in the same directory as
the `build.gradle` source file:

```sh
$ bazel build @org_pubref_rules_require//java/org/pubref/tools/gradle:gendeps_deploy.jar \
  && java -jar ./bazel-bin/external/org_pubref_rules_require/java/org/pubref/tools/gradle/gendeps_deploy.jar \
	-g third_party/aether/build.gradle
```

```sh
third_party/aether/
├── build.gradle
├── BUILD (1)
├── README.md (2)
└── requires.bzl (3)
```

1. The generated `require.bzl` contains a macro defintion that
   `require`'s all the dependencies for a given gradle configuration.
   The common ones are `compile`, `runtime`, `testCompile`, and
   `testRuntime`.  This file should be loaded within your `WORKSPACE`
   (see below).

1. The generated `BUILD` contains a `java_library` rule that exports
   all the dependencies for a given gradle configuration.

1. The generated `README.md` contains a human-readable summary of the
   dependencies.


## 3. Load the generated `requires.bzl` in your WORKSPACE.

Invoke a generated configuration from your `WORKSPACE`.  You can alias
the name of the function to something more specific if needed to avoid
name collisions:

```python
load("//third_party/aether:requires.bzl", aether_runtime = "runtime")
aether_runtime()
```

## Example

1. [examples/aether/build.gradle](examples/aether/build.gradle) (source file).
1. [examples/aether/Makefile](examples/aether/Makefile) (convenience).
1. [examples/aether/BUILD](examples/aether/BUILD) (generated file).
1. [examples/aether/requires.bzl](examples/aether/requires.bzl) (generated file).
1. [examples/aether/README.md](examples/aether/README.md) (generated file).

# Contributing

Contributions welcome; please create Issues or GitHub pull requests.

[bazel-home]: http://bazel.io "Bazel Homepage"
