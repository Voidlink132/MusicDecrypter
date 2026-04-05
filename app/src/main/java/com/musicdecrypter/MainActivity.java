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
    // 国内稳定可访问的解密地址
    private static final String DECRYPT_URL = "https://unlock-music.vercel.app/";
    // 引擎状态常量
    public static final int ENGINE_STATE_LOADING = 0;
    public static final int ENGINE_STATE_READY = 1;
    public static final int ENGINE_STATE_ERROR = 2;
    public static final int ENGINE_STATE_TIMEOUT = 3;
    // 当前引擎状态
    private int engineState = ENGINE_STATE_LOADING;
    // 状态监听列表
    private final List<OnEngineStateChangeListener> stateListeners = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // 最长加载超时15秒，避免无限卡死
    private static final long LOAD_TIMEOUT = 15 * 1000L;
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
        initDecryptWebView();
    }

    private void initBottomNav() {
        viewPager = findViewById(R.id.view_pager);
        bottomNav = findViewById(R.id.bottom_nav);

        FragmentPagerAdapter adapter = new FragmentPagerAdapter(this);
        viewPager.setAdapter(adapter);
        viewPager.setUserInputEnabled(false);

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
        // 开启WebView调试，方便排查问题
        WebView.setWebContentsDebuggingEnabled(true);
        decryptWebView = new WebView(getApplicationContext());
        WebSettings webSettings = decryptWebView.getSettings();

        // WebView核心配置，确保页面和JS正常加载
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
        webSettings.setUserAgentString("Mozilla/5.0 (Linux; Android 14) Chrome/120.0.0.0 Mobile Safari/537.36");

        // 页面加载进度+JS控制台日志监听
        decryptWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                // 打印加载进度到Logcat
                android.util.Log.d("DecryptEngine", "页面加载进度：" + newProgress + "%");
                if (engineState == ENGINE_STATE_LOADING) {
                    notifyStateChange(ENGINE_STATE_LOADING, "正在初始化解密引擎..." + newProgress + "%");
                }
            }

            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage consoleMessage) {
                // 打印JS控制台日志，定位JS执行报错
                android.util.Log.d("DecryptEngine_JS", consoleMessage.message() + " -- 行号：" + consoleMessage.lineNumber());
                return super.onConsoleMessage(consoleMessage);
            }
        });

        // 页面加载状态、错误、重定向全监听
        decryptWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                android.util.Log.d("DecryptEngine", "开始加载页面：" + url);
                startLoadTimeout();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                android.util.Log.d("DecryptEngine", "页面加载完成：" + url);
                cancelTimeout();
                // 延迟500ms等待JS环境完全初始化
                mainHandler.postDelayed(() -> {
                    engineState = ENGINE_STATE_READY;
                    notifyStateChange(ENGINE_STATE_READY, "解密引擎就绪");
                }, 500);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                android.util.Log.e("DecryptEngine", "页面加载失败！错误码：" + errorCode + "，描述：" + description + "，地址：" + failingUrl);
                cancelTimeout();
                engineState = ENGINE_STATE_ERROR;
                notifyStateChange(ENGINE_STATE_ERROR, "引擎加载失败：" + description);
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                super.onReceivedHttpError(view, request, errorResponse);
                android.util.Log.e("DecryptEngine", "HTTP请求失败！地址：" + request.getUrl() + "，状态码：" + errorResponse.getStatusCode());
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                android.util.Log.w("DecryptEngine", "SSL证书错误，忽略并继续加载");
                handler.proceed();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                android.util.Log.d("DecryptEngine", "页面跳转：" + request.getUrl());
                return super.shouldOverrideUrlLoading(view, request);
            }
        });

        // 开始加载解密页面
        decryptWebView.loadUrl(DECRYPT_URL);
    }

    // 启动加载超时计时器
    private void startLoadTimeout() {
        cancelTimeout();
        timeoutRunnable = () -> {
            if (engineState == ENGINE_STATE_LOADING) {
                engineState = ENGINE_STATE_TIMEOUT;
                notifyStateChange(ENGINE_STATE_TIMEOUT, "引擎加载超时，请检查网络后重启APP");
                decryptWebView.stopLoading();
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

    // 注册引擎状态监听
    public void addEngineStateListener(OnEngineStateChangeListener listener) {
        if (!stateListeners.contains(listener)) {
            stateListeners.add(listener);
            listener.onEngineStateChange(engineState, getStateMessage());
        }
    }

    // 移除引擎状态监听
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

    // 获取当前状态文案
    private String getStateMessage() {
        switch (engineState) {
            case ENGINE_STATE_READY: return "解密引擎就绪";
            case ENGINE_STATE_ERROR: return "引擎加载失败";
            case ENGINE_STATE_TIMEOUT: return "引擎加载超时";
            default: return "正在初始化解密引擎...";
        }
    }

    // 对外暴露的解密方法
    public void startDecrypt(String filePath, String fileName, DecryptBridge.DecryptCallback callback) {
        if (engineState != ENGINE_STATE_READY) {
            callback.onDecryptFailed("解密引擎未就绪，当前状态：" + getStateMessage());
            return;
        }

        cancelTimeout();
        startLoadTimeout();

        // 重置桥接接口
        decryptWebView.removeJavascriptInterface("AndroidDecryptBridge");
        currentDecryptBridge = new DecryptBridge(callback);
        decryptWebView.addJavascriptInterface(currentDecryptBridge, "AndroidDecryptBridge");

        callback.onDecryptProgress(10, 100, DecryptBridge.STEP_INIT_ENGINE);
        String mimeType = getMimeType(fileName);
        injectDecryptJs(filePath, fileName, mimeType);
    }

    // 解密JS注入逻辑
    private void injectDecryptJs(String filePath, String fileName, String mimeType) {
        String js = "(async ()=>{"
                + "try{"
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
                + "const maxRetry = 50;"
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
                + "AndroidDecryptBridge.onDecryptFailed('解密失败，不支持的文件格式');"
                + "}"
                + "};"
                + "setTimeout(checkDecrypt, 1000);"
                + "}catch(e){"
                + "AndroidDecryptBridge.closeFile();"
                + "AndroidDecryptBridge.onDecryptFailed(e.message);"
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
}
