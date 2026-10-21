-keep class ir.mservices.** { *; }
-dontwarn ir.mservices.**
-keep class **.IabHelper { *; }
-keep class **.IabResult { *; }
-keep class **.Inventory { *; }
-keep class **.Purchase { *; }
-keep class **.Security { *; }
-keep class **.SkuDetails { *; }
-keepclassmembers class app.tradyar.MyketBridge {
    @android.webkit.JavascriptInterface <methods>;
}
