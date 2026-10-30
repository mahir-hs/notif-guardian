# Manifest components (services, receivers, activities) are kept automatically.
# Hidden framework APIs are reached by reflection on framework classes, which R8 doesn't touch.

# Keep readable stack traces in diagnostics logs
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
