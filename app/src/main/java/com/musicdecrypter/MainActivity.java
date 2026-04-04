package com.musicdecrypter;

import android.os.Bundle;
import android.view.ViewParent;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import com.musicdecrypter.ui.SearchFragment;
import com.musicdecrypter.utils.DecryptBridge;

public class MainActivity extends AppCompatActivity {

    private WebView decryptWebView;
    private DecryptBridge currentDecryptBridge;
    private static final String DECRYPT_URL = "https://demo.unlock-music.dev/";
    private boolean isWebViewReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 加载主Fragment，无无效引用
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new SearchFragment())
                    .commit();
        }

        // 初始化全局解密WebView
        initDecryptWebView();
    }

    private void initDecryptWebView() {
        decryptWebView = new WebView(this);
        WebSettings webSettings = decryptWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setUserAgentString("Mozilla/5.0 (Linux; Android 14) Chrome/120.0.0.0 Mobile Safari/537.36");
        WebView.setWebContentsDebuggingEnabled(true);

        decryptWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                isWebViewReady = true;
            }
        });

        decryptWebView.loadUrl(DECRYPT_URL);
    }

    // 对外暴露的解密方法
    public void startDecrypt(String filePath, String fileName, DecryptBridge.DecryptCallback callback) {
        if (!isWebViewReady) {
            callback.onDecryptFailed("解密引擎未就绪，请稍候重试");
            return;
        }

        // 清理旧桥接，避免内存泄漏
        decryptWebView.removeJavascriptInterface("AndroidDecryptBridge");
        currentDecryptBridge = new DecryptBridge(callback);
        decryptWebView.addJavascriptInterface(currentDecryptBridge, "AndroidDecryptBridge");

        String mimeType = getMimeType(fileName);
        injectDecryptJs(filePath, fileName, mimeType);
    }

    // 分块解密JS注入
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
                + "while(true){"
                + "const blockBase64 = AndroidDecryptBridge.readBlock(blockSize);"
                + "if(!blockBase64) break;"
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
                + "const ipt = document.querySelector('input[type=file]') || document.createElement('input');"
                + "ipt.type = 'file';ipt.multiple = true;"
                + "const dt = new DataTransfer();dt.items.add(file);ipt.files = dt.files;"
                + "ipt.dispatchEvent(new Event('change', {bubbles: true}));"
                + "setTimeout(()=>{"
                + "const a = document.querySelector('a[download]');"
                + "if(a && a.href.startsWith('blob:')){"
                + "fetch(a.href).then(r=>r.blob()).then(bl=>{"
                + "const rd = new FileReader();rd.onloadend=()=>{"
                + "const d = rd.result.split(',')[1];"
                + "AndroidDecryptBridge.onDecryptSuccess(a.download, d);"
                + "};"
                + "rd.readAsDataURL(bl);"
                + "});"
                + "}else{"
                + "AndroidDecryptBridge.onDecryptFailed('不支持的文件格式或解密失败');"
                + "}"
                + "}, 5000);"
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

    // 规范销毁WebView
    @Override
    protected void onDestroy() {
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
        super.onDestroy();
    }
}
