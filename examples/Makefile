BAZEL_BIN := `bazel info bazel-bin`
replace_java:
	rpl -vvR -x'.java' 'stack.tools.shell' 'com.google.devtools.build.lib.shell' java/

gen_aether:
	bazel build //java/org/pubref/tools/gradle:gendeps_deploy.jar \
	&& java -jar $(BAZEL_BIN)/java/org/pubref/tools/gradle/gendeps_deploy.jar \
	-g third_party/aether/build.gradle
