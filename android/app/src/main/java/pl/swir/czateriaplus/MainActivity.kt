package pl.swir.czateriaplus

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class MainActivity : Activity() {
    data class VersionItem(val id:String,val channel:String,val label:String,val file:String,val notes:String){ val key:String get()="$channel:$id" }

    companion object {
        private const val CHAT_URL = "https://czateria.interia.pl/"
        private const val FILE_CHOOSER_REQUEST = 7001
        private const val PREFS = "czp_mobile_rebuild"
        private const val PREF_VERSION = "selected_version"
        private const val PREF_CATALOG = "catalog_cache"
        private const val REPO_RAW = "https://raw.githubusercontent.com/Swir/Czateria_PLUS_Android/mobile-rebuild/"
        private const val CATALOG_URL = REPO_RAW + "mobile/catalog.json"
        private const val VERSION_BASE = REPO_RAW + "mobile/versions/"
    }

    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private lateinit var status: TextView
    private var launcher: View? = null
    private var chooser: ValueCallback<Array<Uri>>? = null
    private var adapterJs = ""
    private var chatStarted = false
    private var selectedKey = "stable:10.17.2"
    private var versions = mutableListOf<VersionItem>()
    private val scriptCache = ConcurrentHashMap<String,String>()
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(6,17,27)
        window.navigationBarColor = Color.rgb(6,17,27)
        adapterJs = readAsset("mobile_adapter.js")
        parseCatalog(readAsset("fallback_catalog.json"))?.let { versions = it.toMutableList() }
        selectedKey = prefs.getString(PREF_VERSION, versions.first().key) ?: versions.first().key
        prefs.getString(PREF_CATALOG,null)?.let { parseCatalog(it)?.let { c -> versions=c.toMutableList() } }
        ensureSelection()
        buildUi()
        configureWeb()
        if(savedInstanceState!=null && web.restoreState(savedInstanceState)!=null) chatStarted=true
        showLauncher()
        refreshCatalog(false)
    }

    private fun buildUi(){
        root=FrameLayout(this).apply{setBackgroundColor(Color.rgb(6,17,27))}
        val column=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(6,17,27))}
        root.addView(column,FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT))
        val head=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(10),dp(5),dp(8),dp(3));setBackgroundColor(Color.rgb(6,17,27))}
        val title=TextView(this).apply{text="⚡ CZATeria Plus";textSize=15f;setTextColor(Color.WHITE);setTypeface(typeface,Typeface.BOLD)}
        head.addView(title,LinearLayout.LayoutParams(0,dp(30),1f))
        status=TextView(this).apply{text="WYBIERZ WERSJĘ";textSize=9f;setTextColor(Color.rgb(117,225,245));gravity=Gravity.CENTER;setPadding(dp(7),0,dp(7),0);background=rounded("#0C2635","#1D637A",8)}
        head.addView(status,LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,dp(25)))
        column.addView(head)
        val hs=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;overScrollMode=View.OVER_SCROLL_NEVER;isFillViewport=false;setBackgroundColor(Color.rgb(6,17,27))}
        val nav=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(5),dp(2),dp(5),dp(5))}
        nav.addView(navButton("🚀 Wersje"){showLauncher()})
        nav.addView(navButton("👥 Znajomi"){runJs("window.CZP_MOBILE&&CZP_MOBILE.openFriends&&CZP_MOBILE.openFriends()")})
        nav.addView(navButton("🌈 Kolor"){runJs("window.CZP_MOBILE&&CZP_MOBILE.openColor&&CZP_MOBILE.openColor()")})
        nav.addView(navButton("🎨 Motyw"){runJs("window.CZP_MOBILE&&CZP_MOBILE.openThemes&&CZP_MOBILE.openThemes()")})
        nav.addView(navButton("↻ Odśwież"){if(chatStarted)web.reload()})
        hs.addView(nav);column.addView(hs,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(48)))
        web=WebView(this);column.addView(web,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f))
        setContentView(root)
    }

    private fun navButton(txt:String,action:()->Unit)=Button(this).apply{
        text=txt;isAllCaps=false;textSize=11f;setTextColor(Color.WHITE);background=rounded("#0E2333","#24485E",10);minWidth=0;minHeight=0;setPadding(dp(10),0,dp(10),0)
        layoutParams=LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,dp(40)).apply{marginStart=dp(3);marginEnd=dp(3)}
        setOnClickListener{action()}
    }

    private fun configureWeb(){
        web.settings.apply{javaScriptEnabled=true;domStorageEnabled=true;databaseEnabled=true;cacheMode=WebSettings.LOAD_DEFAULT;allowFileAccess=true;allowContentAccess=true;javaScriptCanOpenWindowsAutomatically=true;setSupportMultipleWindows(false);builtInZoomControls=false;displayZoomControls=false;mediaPlaybackRequiresUserGesture=false;mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW;textZoom=100;useWideViewPort=false;loadWithOverviewMode=false}
        CookieManager.getInstance().apply{setAcceptCookie(true);setAcceptThirdPartyCookies(web,true)}
        web.webViewClient=object:WebViewClient(){
            override fun shouldOverrideUrlLoading(view:WebView?,request:WebResourceRequest?):Boolean{val u=request?.url?:return false;val h=u.host?:return false;return if(h.endsWith("interia.pl"))false else try{startActivity(Intent(Intent.ACTION_VIEW,u));true}catch(_:Exception){false}}
            override fun onPageFinished(view:WebView?,url:String?){super.onPageFinished(view,url);injectAdapter();scheduleVersionInjection()}
        }
        web.webChromeClient=object:WebChromeClient(){
            override fun onShowFileChooser(webView:WebView?,filePathCallback:ValueCallback<Array<Uri>>?,fileChooserParams:FileChooserParams?):Boolean{chooser?.onReceiveValue(null);chooser=filePathCallback;val i=Intent(Intent.ACTION_GET_CONTENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="*/*"};return try{startActivityForResult(Intent.createChooser(i,"Wybierz plik"),FILE_CHOOSER_REQUEST);true}catch(_:Exception){chooser=null;false}}
        }
    }

    private fun scheduleVersionInjection(){ if(!chatStarted)return; listOf(200L,700L,1500L,3000L,5200L).forEach{d->Handler(Looper.getMainLooper()).postDelayed({injectSelectedVersion()},d)} }
    private fun injectAdapter(){if(adapterJs.isNotBlank())web.evaluateJavascript(adapterJs,null)}

    private fun injectSelectedVersion(){
        if(!chatStarted)return
        val v=currentVersion()
        web.evaluateJavascript("(function(){return typeof CHNS==='undefined'?'WAIT':'READY'})();"){r->
            if(r?.contains("WAIT")==true){status.text="${v.channel.uppercase()} ${v.id} • WYBIERZ POKÓJ";return@evaluateJavascript}
            val marker="${v.channel}:${v.id}"
            web.evaluateJavascript("(function(){return window.__CZP_VERSION_LOADED==${jsQuote(marker)}?'YES':'NO'})();"){loaded->
                if(loaded?.contains("YES")==true){injectAdapter();status.text="${v.channel.uppercase()} ${v.id} ✓";return@evaluateJavascript}
                status.text="${v.channel.uppercase()} ${v.id} • ŁADUJĘ…"
                fetchVersionScript(v){script->
                    if(script==null){runOnUiThread{status.text="${v.channel.uppercase()} ${v.id} • BŁĄD POBRANIA"};return@fetchVersionScript}
                    runOnUiThread{
                        injectAdapter()
                        val wrapped="(function(){try{window.__CZP_VERSION_LOADED=${jsQuote(marker)};\n$script\n}catch(e){console.error('CZP version',e);window.__CZP_VERSION_LOADED='';}})();"
                        web.evaluateJavascript(wrapped,null)
                        listOf(400L,1200L,2600L,4800L).forEach{d->Handler(Looper.getMainLooper()).postDelayed({injectAdapter();status.text="${v.channel.uppercase()} ${v.id} ✓"},d)}
                    }
                }
            }
        }
    }

    private fun fetchVersionScript(v:VersionItem,done:(String?)->Unit){scriptCache[v.file]?.let{done(it);return};Thread{try{val raw=downloadText(VERSION_BASE+v.file);scriptCache[v.file]=raw;done(raw)}catch(_:Exception){done(null)}}.start()}

    private fun showLauncher(){
        launcher?.let{root.removeView(it)}
        val overlay=FrameLayout(this).apply{setBackgroundColor(Color.argb(244,3,9,15));isClickable=true;isFocusable=true}
        val sv=ScrollView(this).apply{isFillViewport=true;overScrollMode=View.OVER_SCROLL_NEVER}
        overlay.addView(sv,FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT))
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(16),dp(12),dp(24))}
        sv.addView(body,FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.WRAP_CONTENT))
        val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(14),dp(14),dp(14));background=rounded("#081522","#176681",18)}
        body.addView(card,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT))
        val tr=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL};card.addView(tr)
        tr.addView(TextView(this).apply{text="⚡ Wybierz wersję";textSize=19f;setTextColor(Color.WHITE);setTypeface(typeface,Typeface.BOLD)},LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
        if(chatStarted)tr.addView(Button(this).apply{text="✕";isAllCaps=false;textSize=16f;minWidth=0;minHeight=0;setTextColor(Color.WHITE);background=rounded("#122536","#31536A",10);setOnClickListener{removeLauncher()}},LinearLayout.LayoutParams(dp(44),dp(42)))
        card.addView(TextView(this).apply{text="3 najnowsze Stable + 3 najnowsze Beta. Skrypty są sklonowane do repo Czateria_PLUS_Android, a UI jest przebudowane specjalnie pod telefon.";textSize=11f;setTextColor(Color.rgb(151,176,194));setPadding(0,dp(7),0,dp(9))})
        addSection(card,"🛡️ STABLE","#00E5FF",versions.filter{it.channel=="stable"}.take(3))
        addSection(card,"🧪 BETA","#FF69DF",versions.filter{it.channel=="beta"}.take(3))
        card.addView(Button(this).apply{text="↻ Pobierz aktualny katalog";isAllCaps=false;textSize=12f;setTextColor(Color.WHITE);background=rounded("#102333","#2C5268",12);setOnClickListener{refreshCatalog(true)}},LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(46)).apply{topMargin=dp(7)})
        launcher=overlay;root.addView(overlay,FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun addSection(parent:LinearLayout,title:String,accent:String,list:List<VersionItem>){parent.addView(TextView(this).apply{text=title;textSize=14f;setTypeface(typeface,Typeface.BOLD);setTextColor(Color.parseColor(accent));setPadding(dp(2),dp(11),0,dp(6))});list.forEachIndexed{i,v->parent.addView(versionCard(v,i==0,accent))}}
    private fun versionCard(v:VersionItem,newest:Boolean,accent:String):View{
        val chosen=selectedKey==v.key
        return LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(10),dp(12),dp(10));background=rounded(if(chosen)"#123044" else "#0D1C2A",if(chosen)"#FFFFFF" else accent,13)
            val row=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            row.addView(TextView(this@MainActivity).apply{text=v.label;textSize=13f;setTextColor(Color.WHITE);setTypeface(typeface,Typeface.BOLD)},LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
            row.addView(TextView(this@MainActivity).apply{text=if(chosen)"WYBRANA" else if(newest)"NAJNOWSZA" else v.channel.uppercase();textSize=8f;setTextColor(Color.parseColor(accent));setPadding(dp(6),dp(3),dp(6),dp(3));background=rounded("#111C27",accent,14)})
            addView(row);if(v.notes.isNotBlank())addView(TextView(this@MainActivity).apply{text=v.notes;textSize=10f;setTextColor(Color.rgb(150,176,194));setPadding(0,dp(5),0,dp(7))})
            addView(Button(this@MainActivity).apply{text=if(chosen&&chatStarted)"✓ URUCHOM PONOWNIE ${v.id}" else "URUCHOM ${v.id}";isAllCaps=false;textSize=11f;setTextColor(Color.WHITE);background=rounded(if(v.channel=="beta")"#35142F" else "#0A3040",accent,10);setOnClickListener{chooseVersion(v)}},LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(43)))
        }.apply{layoutParams=LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT).apply{bottomMargin=dp(7)}}
    }

    private fun chooseVersion(v:VersionItem){selectedKey=v.key;prefs.edit().putString(PREF_VERSION,selectedKey).apply();scriptCache.clear();removeLauncher();status.text="${v.channel.uppercase()} ${v.id} • START";if(!chatStarted){chatStarted=true;web.loadUrl(CHAT_URL)}else web.reload()}
    private fun removeLauncher(){launcher?.let{root.removeView(it)};launcher=null}
    private fun currentVersion()=versions.firstOrNull{it.key==selectedKey}?:versions.first()
    private fun ensureSelection(){if(versions.none{it.key==selectedKey}){selectedKey=versions.firstOrNull{it.channel=="stable"}?.key?:versions.first().key;prefs.edit().putString(PREF_VERSION,selectedKey).apply()}}

    private fun refreshCatalog(show:Boolean){if(show)status.text="KATALOG • SPRAWDZAM";Thread{try{val raw=downloadText(CATALOG_URL);val c=parseCatalog(raw)?:throw IllegalStateException();prefs.edit().putString(PREF_CATALOG,raw).apply();runOnUiThread{versions=c.toMutableList();ensureSelection();status.text="KATALOG ✓";if(launcher!=null)showLauncher()}}catch(_:Exception){runOnUiThread{if(show)status.text="KATALOG • OFFLINE"}}}.start()}
    private fun parseCatalog(raw:String):List<VersionItem>?=try{val a=JSONObject(raw).getJSONArray("versions");val out=mutableListOf<VersionItem>();for(i in 0 until a.length()){val o=a.getJSONObject(i);out+=VersionItem(o.getString("id"),o.getString("channel"),o.optString("label",o.getString("id")),o.getString("file"),o.optString("notes",""))};out}catch(_:Exception){null}
    private fun downloadText(url:String):String{val c=(URL(url).openConnection() as HttpURLConnection).apply{connectTimeout=7000;readTimeout=9000;requestMethod="GET";useCaches=false;setRequestProperty("Cache-Control","no-cache");setRequestProperty("User-Agent","CZATeria-Plus-Mobile")};if(c.responseCode !in 200..299)throw IllegalStateException("HTTP ${c.responseCode}");return c.inputStream.bufferedReader().use{it.readText()}.also{c.disconnect()}}
    private fun runJs(code:String){if(!chatStarted)return;web.evaluateJavascript("(function(){try{$code}catch(e){console.error(e)}})();",null)}
    private fun jsQuote(s:String)="'"+s.replace("\\","\\\\").replace("'","\\'").replace("\r","\\r").replace("\n","\\n")+"'"
    private fun readAsset(n:String)=assets.open(n).use{BufferedReader(InputStreamReader(it)).readText()}
    private fun rounded(fill:String,stroke:String,r:Int)=GradientDrawable().apply{shape=GradientDrawable.RECTANGLE;cornerRadius=dp(r).toFloat();setColor(Color.parseColor(fill));setStroke(dp(1),Color.parseColor(stroke))}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()

    @Deprecated("Deprecated Android API")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(requestCode==FILE_CHOOSER_REQUEST){chooser?.onReceiveValue(if(resultCode==RESULT_OK&&data?.data!=null)arrayOf(data.data!!) else null);chooser=null}}
    override fun onBackPressed(){if(launcher!=null&&chatStarted){removeLauncher();return};if(::web.isInitialized&&web.canGoBack())web.goBack() else super.onBackPressed()}
    override fun onSaveInstanceState(outState:Bundle){if(::web.isInitialized)web.saveState(outState);super.onSaveInstanceState(outState)}
    override fun onDestroy(){if(::web.isInitialized){web.stopLoading();web.destroy()};super.onDestroy()}
}
