# jaudiotagger finds its tag readers and field types by name, so keep them whole.
-keep class org.jaudiotagger.** { *; }
# Desktop-only parts of jaudiotagger (image and UI classes) that Android never calls.
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn org.jaudiotagger.**
