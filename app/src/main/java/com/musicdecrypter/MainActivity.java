package com.musicdecrypter;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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

    private static final String TAG = "MusicDecrypter_Main";
    private ViewPager2 viewPager;
    private BottomNavigationView bottomNav;
    private WebView decryptWebView;
    private DecryptBridge currentDecryptBridge;
    // 本地离线解密页面
    private static final String DECRYPT_URL = "file:///android_asset/index.html";
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
    // 本地页面加载超时3秒
    private static final long LOAD_TIMEOUT = 3 * 1000L;
    private Runnable timeoutRunnable;

    // 引擎状态监听接口
    public interface OnEngineStateChangeListener {
        void onEngineStateChange(int state, String message);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "===== MainActivity onCreate 启动 =====");
        setContentView(R.layout.activity_main);
        initBottomNav();
        if (decryptWebView == null) {
            Log.d(TAG, "开始初始化WebView解密引擎");
            initDecryptWebView();
        }
    }

    private void initBottomNav() {
        viewPager = findViewById(R.id.view_pager);
        bottomNav = findViewById(R.id.bottom_nav);
        FragmentPagerAdapter adapter = new FragmentPagerAdapter(this);
        viewPager.setAdapter(adapter);
        viewPager.setUserInputEnabled(false);
        viewPager.setOffscreenPageLimit(2);

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
        webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        Log.d(TAG, "WebView配置完成");

        // 页面加载进度+JS日志监听
        decryptWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                Log.d(TAG, "本地页面加载进度：" + newProgress + "%");
                if (engineState == ENGINE_STATE_LOADING) {
                    notifyStateChange(ENGINE_STATE_LOADING, "正在初始化解密引擎..." + newProgress + "%");
                }
            }

            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage consoleMessage) {
                Log.d("DecryptEngine_JS", consoleMessage.message() + " | 行号：" + consoleMessage.lineNumber());
                return super.onConsoleMessage(consoleMessage);
            }
        });

        // 【核心修复】页面加载生命周期监听，提前注入桥接对象
        decryptWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                Log.d(TAG, "页面开始加载：" + url);
                engineState = ENGINE_STATE_LOADING;
                startLoadTimeout();
                notifyStateChange(ENGINE_STATE_LOADING, "正在初始化解密引擎...");
                // 页面开始加载时，先移除旧的桥接对象，避免残留
                decryptWebView.removeJavascriptInterface("AndroidDecryptBridge");
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "页面加载完成：" + url);
                cancelTimeout();

                // 【核心修复】页面加载完成后，立即注入桥接对象，提前挂载到JS上下文
                mainHandler.postDelayed(() -> {
                    // 初始化空的桥接对象，提前挂载到window上
                    currentDecryptBridge = new DecryptBridge(null);
                    decryptWebView.addJavascriptInterface(currentDecryptBridge, "AndroidDecryptBridge");
                    Log.d(TAG, "桥接对象已提前注入，挂载到JS上下文");

                    engineState = ENGINE_STATE_READY;
                    notifyStateChange(ENGINE_STATE_READY, "解密引擎就绪");
                    Log.d(TAG, "===== 本地解密引擎加载完成，就绪 =====");
                }, 200);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                cancelTimeout();
                engineState = ENGINE_STATE_ERROR;
                String errorMsg = "引擎加载失败：" + description + "（错误码：" + errorCode + "）";
                notifyStateChange(ENGINE_STATE_ERROR, errorMsg);
                Log.e(TAG, errorMsg);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
        });

        decryptWebView.loadUrl(DECRYPT_URL);
    }

    // 启动超时计时器
    private void startLoadTimeout() {
        cancelTimeout();
        timeoutRunnable = () -> {
            if (engineState == ENGINE_STATE_LOADING) {
                engineState = ENGINE_STATE_TIMEOUT;
                String errorMsg = "引擎加载超时，请重启APP";
                notifyStateChange(ENGINE_STATE_TIMEOUT, errorMsg);
                decryptWebView.stopLoading();
                Log.e(TAG, errorMsg);
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
        Log.d(TAG, "注册引擎状态监听");
        if (!stateListeners.contains(listener)) {
            stateListeners.add(listener);
            listener.onEngineStateChange(engineState, getStateMessage());
        }
    }

    // 移除状态监听
    public void removeEngineStateListener(OnEngineStateChangeListener listener) {
        Log.d(TAG, "移除引擎状态监听");
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

    // 【核心修复】对外解密方法，更新桥接对象的回调，不重复注入
    public void startDecrypt(String filePath, String fileName, DecryptBridge.DecryptCallback callback) {
        Log.d(TAG, "收到解密请求，文件名：" + fileName);
        if (engineState != ENGINE_STATE_READY) {
            String error = "解密引擎未就绪，当前状态：" + getStateMessage();
            Log.e(TAG, error);
            callback.onDecryptFailed(error);
            return;
        }

        cancelTimeout();
        startLoadTimeout();

        // 【核心修复】不重复注入，仅更新当前桥接对象的回调
        if (currentDecryptBridge != null) {
            currentDecryptBridge.updateCallback(callback);
            Log.d(TAG, "桥接对象回调已更新，开始解密");
        } else {
            // 兜底：如果桥接对象丢失，重新注入
            currentDecryptBridge = new DecryptBridge(callback);
            decryptWebView.addJavascriptInterface(currentDecryptBridge, "AndroidDecryptBridge");
            Log.d(TAG, "桥接对象丢失，重新注入完成");
        }

        callback.onDecryptProgress(10, 100, DecryptBridge.STEP_INIT_ENGINE);
        String mimeType = getMimeType(fileName);
        injectDecryptJs(filePath, fileName, mimeType);
    }

    // 【核心修复】解密JS逻辑，先等待桥接对象存在，再执行解密
    private void injectDecryptJs(String filePath, String fileName, String mimeType) {
        String js = "(async ()=>{"
                + "try{"
                + "// 【核心修复】循环等待桥接对象挂载到window上，最多等待2秒"
                + "let waitCount = 0;"
                + "const maxWait = 20;"
                + "while(typeof AndroidDecryptBridge === 'undefined' && waitCount < maxWait){"
                + "await new Promise(resolve => setTimeout(resolve, 100));"
                + "waitCount++;"
                + "}"
                + "// 再次校验，不存在直接抛出错误"
                + "if(typeof AndroidDecryptBridge === 'undefined') {"
                + "throw new Error('等待超时，AndroidDecryptBridge 桥接对象未找到');"
                + "}"
                + "console.log('桥接对象已找到，开始解密');"
                + "const filePath = '" + filePath.replace("'", "\\'") + "';"
                + "const fileName = '" + fileName.replace("'", "\\'") + "';"
                + "const mimeType = '" + mimeType + "';"
                + "const blockSize = 1024 * 1024;"
                + "const fileSize = AndroidDecryptBridge.openFile(filePath);"
                + "if(fileSize < 0) throw new Error('无法打开音乐文件');"
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
                + "const fileInput = document.querySelector('input[type=\"file\"]') || document.createElement('input');"
                + "fileInput.type = 'file';fileInput.multiple = true;fileInput.accept = 'audio/*';"
                + "const dataTransfer = new DataTransfer();"
                + "dataTransfer.items.add(file);"
                + "fileInput.files = dataTransfer.files;"
                + "fileInput.dispatchEvent(new Event('change', {bubbles: true, cancelable: true}));"
                + "AndroidDecryptBridge.onDecryptProgressUpdate(40);"
                + "let retryCount = 0;"
                + "const maxRetry = 40;"
                + "const checkInterval = 400;"
                + "const checkDecryptResult = ()=>{"
                + "retryCount++;"
                + "const downloadLink = document.querySelector('a[download]') || document.querySelector('a[href^=\"blob:\"]');"
                + "if(downloadLink && downloadLink.href && downloadLink.href.startsWith('blob:')){"
                + "AndroidDecryptBridge.onDecryptProgressUpdate(80);"
                + "fetch(downloadLink.href).then(res=>res.blob()).then(decryptBlob=>{"
                + "const reader = new FileReader();"
                + "reader.onloadend = ()=>{"
                + "const base64Data = reader.result.split(',')[1];"
                + "const saveName = downloadLink.download || fileName.replace(/\\.\\w+$/, '.flac');"
                + "AndroidDecryptBridge.onDecryptSuccess(saveName, base64Data);"
                + "};"
                + "reader.readAsDataURL(decryptBlob);"
                + "}).catch(err=>{"
                + "AndroidDecryptBridge.onDecryptFailed('读取解密文件失败：' + err.message);"
                + "});"
                + "}else if(retryCount < maxRetry){"
                + "const progress = 40 + Math.floor((retryCount / maxRetry) * 40);"
                + "AndroidDecryptBridge.onDecryptProgressUpdate(progress);"
                + "setTimeout(checkDecryptResult, checkInterval);"
                + "}else{"
                + "AndroidDecryptBridge.onDecryptFailed('解密超时，不支持的文件格式');"
                + "}"
                + "};"
                + "setTimeout(checkDecryptResult, 1200);"
                + "}catch(e){"
                + "if(typeof AndroidDecryptBridge !== 'undefined') {"
                + "AndroidDecryptBridge.closeFile();"
                + "AndroidDecryptBridge.onDecryptFailed('解密执行异常：' + e.message);"
                + "}"
                + "console.error('解密JS执行错误：', e);"
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
        Log.d(TAG, "MainActivity销毁，释放WebView资源");
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

    public int getEngineState() {
        return engineState;
    }
}
