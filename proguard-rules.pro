# The protobuf Lite runtime internally uses reflection, see
#   https://github.com/protocolbuffers/protobuf/blob/main/java/lite.md#r8-rule-to-make-production-app-builds-work
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

# This is compiled as a stub, hence names should not be obfuscated
-keep class android.car.** { *; }