# Release build: shrink unused library code, but keep every name so stored settings
# (theme names, quote groups) and stack traces stay readable.
-dontobfuscate

# The app's own classes are small; keep them whole so nothing reached by name
# (manifest components, saved enum names, RemoteViews calls) can be removed.
-keep class com.trinineba.quotesonlock.** { *; }
