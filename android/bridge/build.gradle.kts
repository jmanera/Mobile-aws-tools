// Exposes the gomobile AAR built by bridge/build-aar.sh as a project dependency.
// AGP does not allow local .aar files inside a library module, so this module
// publishes the file as its only artifact instead of compiling anything.
configurations.maybeCreate("default")
artifacts.add("default", file("libs/ssmbridge.aar"))
