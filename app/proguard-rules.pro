# Add project specific ProGuard rules here.

# Pont Javascript _JSAPI : les methodes sont appelees par leur nom depuis
# le JS de la WebView, elles ne doivent jamais etre renommees/supprimees.
-keepclassmembers class com.amarullz.androidtv.animetvjmto.AnimeView$JSViewApi {
    public *;
}
-keepattributes JavascriptInterface

# Conserver les numeros de ligne pour les rapports de crash.
-keepattributes SourceFile,LineNumberTable
