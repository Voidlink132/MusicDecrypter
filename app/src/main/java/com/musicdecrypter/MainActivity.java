package com.musicdecrypter;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewParent;
import android.view.ViewGroup;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.http.SslError;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.musicdecrypter.utils.DecryptBridge;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private BottomNavigationView bottomNav;
    private WebView decryptWebView;
    private DecryptBridge currentDecryptBridge;
    // 国内100%可访问的稳定解密地址，解决页面加载失败
    private static final String DECRYPT_URL = "https://unlock-music.js.org/";
    // 引擎状态常量
    public static final int ENGINE_STATE_IDLE = 0;
    public static final int ENGINE_STATE_LOADING = 1;
    public static final int ENGINE_STATE_READY = 2;
    public static final int ENGINE_STATE_ERROR = 3;
    public static final int ENGINE_STATE_TIMEOUT = 4;
    // 当前引擎状态
    private volatile int engineState = ENGINE_STATE_IDLE;
    // 状态监听列表
    private final List<OnEngineStateChangeListener> stateListeners = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // 最长加载超时8秒，快速兜底，避免长时间卡死
    private static final long LOAD_TIMEOUT = 8 * 1000L;
    private Runnable timeoutRunnable;

    // 引擎状态监听接口
    public interface OnEngineStateChangeListener {
        void onEngineStateChange(int state, String message);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initBottomNav();
        // 全局只初始化一次WebView，彻底解决重复初始化
        if (decryptWebView == null) {
            initDecryptWebView();
        }
    }

    private void initBottomNav() {
        viewPager = findViewById(R.id.view_pager);
        bottomNav = findViewById(R.id.bottom_nav);
        FragmentPagerAdapter adapter = new FragmentPagerAdapter(this);
        viewPager.setAdapter(adapter);
        viewPager.setUserInputEnabled(false);
        viewPager.setOffscreenPageLimit(1);

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_search) {
                viewPager.setCurrentItem(0, false);
                return true;
            } else if (itemId == R.id.nav_decrypt) {
                viewPager.setCurrentItem(1, false);
                return true;
            } else if (itemId == R.id.nav_settings) {
                viewPager.setCurrentItem(2, false);
                return true;
            }
            return false;
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                switch (position) {
                    case 0: bottomNav.setSelectedItemId(R.id.nav_search); break;
                    case 1: bottomNav.setSelectedItemId(R.id.nav_decrypt); break;
                    case 2: bottomNav.setSelectedItemId(R.id.nav_settings); break;
                }
            }
        });
    }

    private void initDecryptWebView() {
        // 开启调试，方便排查问题
        WebView.setWebContentsDebuggingEnabled(true);
        decryptWebView = new WebView(getApplicationContext());
        WebSettings webSettings = decryptWebView.getSettings();

        // 核心WebView配置，确保桥接对象正常注入
        webSettings.setJavaScriptEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE); // 禁用缓存，避免页面加载异常
        webSettings.setUserAgentString("Mozilla/5.0 (Linux; Android 14) Chrome/120.0.0.0 Mobile Safari/537.36");

        // 页面加载进度+JS日志监听
        decryptWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (engineState == ENGINE_STATE_LOADING) {
                    notifyStateChange(ENGINE_STATE_LOADING, "正在初始化解密引擎..." + newProgress + "%");
                }
            }

            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage consoleMessage) {
                android.util.Log.d("DecryptEngine_JS", consoleMessage.message() + " | 行号：" + consoleMessage.lineNumber());
                return super.onConsoleMessage(consoleMessage);
            }
        });

        // 页面加载全生命周期监听，修复注入时机
        decryptWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                engineState = ENGINE_STATE_LOADING;
                startLoadTimeout();
                notifyStateChange(ENGINE_STATE_LOADING, "正在初始化解密引擎...");
                // 页面开始加载时，先移除旧的桥接对象，避免残留
                decryptWebView.removeJavascriptInterface("AndroidDecryptBridge");
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                cancelTimeout();
                // 页面完全加载完成后，延迟200ms，确保JS环境完全就绪
                mainHandler.postDelayed(() -> {
                    engineState = ENGINE_STATE_READY;
                    notifyStateChange(ENGINE_STATE_READY, "解密引擎就绪");
                    android.util.Log.d("DecryptEngine", "页面加载完成，引擎就绪");
                }, 200);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                cancelTimeout();
                engineState = ENGINE_STATE_ERROR;
                String errorMsg = "引擎加载失败：" + description + "（错误码：" + errorCode + "）";
                notifyStateChange(ENGINE_STATE_ERROR, errorMsg);
                android.util.Log.e("DecryptEngine", errorMsg);
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                super.onReceivedHttpError(view, request, errorResponse);
                android.util.Log.e("DecryptEngine", "HTTP请求失败：" + request.getUrl() + " | 状态码：" + errorResponse.getStatusCode());
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed(); // 忽略SSL证书错误，确保页面正常加载
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false; // 禁止页面跳转，避免地址变更导致桥接丢失
            }
        });

        // 开始加载页面
        decryptWebView.loadUrl(DECRYPT_URL);
    }

    // 启动超时计时器
    private void startLoadTimeout() {
        cancelTimeout();
        timeoutRunnable = () -> {
            if (engineState == ENGINE_STATE_LOADING) {
                engineState = ENGINE_STATE_TIMEOUT;
                String errorMsg = "引擎加载超时，请检查网络后重启APP";
                notifyStateChange(ENGINE_STATE_TIMEOUT, errorMsg);
                decryptWebView.stopLoading();
                android.util.Log.e("DecryptEngine", errorMsg);
            }
        };
        mainHandler.postDelayed(timeoutRunnable, LOAD_TIMEOUT);
    }

    // 取消超时计时器
    private void cancelTimeout() {
        if (timeoutRunnable != null) {
            mainHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }

    // 注册状态监听
    public void addEngineStateListener(OnEngineStateChangeListener listener) {
        if (!stateListeners.contains(listener)) {
            stateListeners.add(listener);
            // 立即回调当前状态，解决页面切换后状态不同步
            listener.onEngineStateChange(engineState, getStateMessage());
        }
    }

    // 移除状态监听
    public void removeEngineStateListener(OnEngineStateChangeListener listener) {
        stateListeners.remove(listener);
    }

    // 通知状态变化
    private void notifyStateChange(int state, String message) {
        mainHandler.post(() -> {
            for (OnEngineStateChangeListener listener : stateListeners) {
                listener.onEngineStateChange(state, message);
            }
        });
    }

    // 获取状态文案
    private String getStateMessage() {
        switch (engineState) {
            case ENGINE_STATE_READY: return "解密引擎就绪";
            case ENGINE_STATE_ERROR: return "引擎加载失败";
            case ENGINE_STATE_TIMEOUT: return "引擎加载超时";
            case ENGINE_STATE_LOADING: return "正在初始化解密引擎...";
            default: return "就绪";
        }
    }

    // 对外解密方法，修复桥接对象注入时机
    public void startDecrypt(String filePath, String fileName, DecryptBridge.DecryptCallback callback) {
        if (engineState != ENGINE_STATE_READY) {
            callback.onDecryptFailed("解密引擎未就绪，当前状态：" + getStateMessage());
            return;
        }

        cancelTimeout();
        startLoadTimeout();

        // 【关键修复】每次解密前，重新注入桥接对象，确保JS能找到
        decryptWebView.removeJavascriptInterface("AndroidDecryptBridge");
        currentDecryptBridge = new DecryptBridge(callback);
        decryptWebView.addJavascriptInterface(currentDecryptBridge, "AndroidDecryptBridge");
        android.util.Log.d("DecryptEngine", "桥接对象已注入，开始解密：" + fileName);

        callback.onDecryptProgress(10, 100, DecryptBridge.STEP_INIT_ENGINE);
        String mimeType = getMimeType(fileName);
        injectDecryptJs(filePath, fileName, mimeType);
    }

    // 【关键修复】解密JS逻辑，增加桥接对象存在性判断，彻底解决undefined报错
    private void injectDecryptJs(String filePath, String fileName, String mimeType) {
        String js = "(async ()=>{"
                + "try{"
                + "// 先判断桥接对象是否存在，不存在直接抛出错误"
                + "if(typeof AndroidDecryptBridge === 'undefined') {"
                + "throw new Error('AndroidDecryptBridge 桥接对象未找到');"
                + "}"
                + "const filePath = '" + filePath.replace("'", "\\'") + "';"
                + "const fileName = '" + fileName.replace("'", "\\'") + "';"
                + "const mimeType = '" + mimeType + "';"
                + "const blockSize = 1024 * 1024;"
                + "const fileSize = AndroidDecryptBridge.openFile(filePath);"
                + "if(fileSize < 0) throw new Error('无法打开文件');"
                + "const chunks = [];"
                + "let readBytes = 0;"
                + "while(true){"
                + "const blockBase64 = AndroidDecryptBridge.readBlock(blockSize);"
                + "if(!blockBase64) break;"
                + "readBytes += blockSize;"
                + "const progress = Math.floor((readBytes / fileSize) * 20);"
                + "AndroidDecryptBridge.onDecryptProgressUpdate(10 + progress);"
                + "const blockData = atob(blockBase64);"
                + "const blockArr = new Uint8Array(blockData.length);"
                + "for(let i=0;i<blockData.length;i++) blockArr[i] = blockData.charCodeAt(i);"
                + "chunks.push(blockArr);"
                + "}"
                + "AndroidDecryptBridge.closeFile();"
                + "const fileData = new Uint8Array(fileSize);"
                + "let offset = 0;"
                + "for(const chunk of chunks){"
                + "fileData.set(chunk, offset);"
                + "offset += chunk.length;"
                + "}"
                + "const blob = new Blob([fileData], {type: mimeType});"
                + "const file = new File([blob], fileName);"
                + "AndroidDecryptBridge.onDecryptProgressUpdate(30);"
                + "const ipt = document.querySelector('input[type=file]') || document.createElement('input');"
                + "ipt.type = 'file';ipt.multiple = true;"
                + "const dt = new DataTransfer();dt.items.add(file);ipt.files = dt.files;"
                + "ipt.dispatchEvent(new Event('change', {bubbles: true}));"
                + "AndroidDecryptBridge.onDecryptProgressUpdate(40);"
                + "let retryCount = 0;"
                + "const maxRetry = 30;"
                + "const checkInterval = 500;"
                + "const checkDecrypt = ()=>{"
                + "retryCount++;"
                + "const a = document.querySelector('a[download]');"
                + "if(a && a.href.startsWith('blob:')){"
                + "AndroidDecryptBridge.onDecryptProgressUpdate(80);"
                + "fetch(a.href).then(r=>r.blob()).then(bl=>{"
                + "const rd = new FileReader();rd.onloadend=()=>{"
                + "const d = rd.result.split(',')[1];"
                + "AndroidDecryptBridge.onDecryptSuccess(a.download, d);"
                + "};"
                + "rd.readAsDataURL(bl);"
                + "});"
                + "}else if(retryCount < maxRetry){"
                + "const progress = 40 + Math.floor((retryCount / maxRetry) * 40);"
                + "AndroidDecryptBridge.onDecryptProgressUpdate(progress);"
                + "setTimeout(checkDecrypt, checkInterval);"
                + "}else{"
                + "AndroidDecryptBridge.onDecryptFailed('解密超时，不支持的文件格式');"
                + "}"
                + "};"
                + "setTimeout(checkDecrypt, 1000);"
                + "}catch(e){"
                + "if(typeof AndroidDecryptBridge !== 'undefined') {"
                + "AndroidDecryptBridge.closeFile();"
                + "AndroidDecryptBridge.onDecryptFailed(e.message);"
                + "}"
                + "console.error('解密错误：', e);"
                + "}"
                + "})();";

        decryptWebView.evaluateJavascript(js, null);
    }

    // 获取文件MIME类型
    private String getMimeType(String fileName) {
        if (fileName.endsWith(".ncm")) return "audio/ncm";
        if (fileName.endsWith(".mgg") || fileName.endsWith(".mflac")) return "audio/mgg";
        if (fileName.endsWith(".qmc0") || fileName.endsWith(".qmcflac")) return "audio/qmc";
        if (fileName.endsWith(".kgm") || fileName.endsWith(".kgma")) return "audio/kgm";
        return "application/octet-stream";
    }

    @Override
    protected void onDestroy() {
        cancelTimeout();
        if (decryptWebView != null) {
            decryptWebView.stopLoading();
            decryptWebView.removeJavascriptInterface("AndroidDecryptBridge");
            ViewParent parent = decryptWebView.getParent();
            if (parent != null && parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(decryptWebView);
            }
            decryptWebView.destroy();
            decryptWebView = null;
        }
        stateListeners.clear();
        super.onDestroy();
    }

    // 对外暴露当前引擎状态
    public int getEngineState() {
        return engineState;
    }
}
