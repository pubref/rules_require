java_binary(
    name = "gendeps",
    main_class = "org.pubref.tools.gradle.GenRequires",
    srcs = [
        "GenRequires.java",
        "GenRequiresOptions.java",
    ],
    deps = [
        "//java/com/google/devtools/common/options",
        "//java/com/google/devtools/build/lib/shell",
        "@com_google_guava_guava//jar",
    ],
    visibility = ["//visibility:public"],
)
