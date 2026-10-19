# PDFBox-Android and jsoup load fonts, glyph lists, CMaps and HTML entities as resources next to
# their classes, so their package names and classes must stay as they are.
-keep class com.tom_roush.** { *; }
-keep class org.jsoup.** { *; }

# Optional JPEG 2000 support in PDFBox-Android that is not shipped.
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-dontwarn javax.annotation.**
-dontwarn org.jspecify.**
-dontwarn com.google.re2j.**
