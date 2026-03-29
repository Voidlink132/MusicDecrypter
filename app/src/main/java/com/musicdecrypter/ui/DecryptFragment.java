package com.musicdecrypter.ui;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.musicdecrypter.databinding.FragmentDecryptBinding;
import com.musicdecrypter.utils.DecryptBridge;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class DecryptFragment extends Fragment implements DecryptBridge.DecryptCallback {

    private FragmentDecryptBinding binding;
    private static final String TARGET_URL = "https://demo.unlock-music.dev/";
    private boolean isWebViewReady = false;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final List<Uri> pendingFileUris = new ArrayList<>();
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);
    private int totalFileCount = 0;

    private final ActivityResultLauncher<Intent> fileChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                    return;
                }

                pendingFileUris.clear();
                successCount.set(0);
                failedCount.set(0);

                if (result.getData().getClipData() != null) {
                    int count = result.getData().getClipData().getItemCount();
                    for (int i = 0; i < count; i++) {
                        Uri uri = result.getData().getClipData().getItemAt(i).getUri();
                        pendingFileUris.add(uri);
                    }
                } else if (result.getData().getData() != null) {
                    pendingFileUris.add(result.getData().getData());
                }

                totalFileCount = pendingFileUris.size();
                if (totalFileCount == 0) {
                    Toast.makeText(requireContext(), "未选择任何文件", Toast.LENGTH_SHORT).show();
                    return;
                }

                startDecryptQueue();
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDecryptBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initWebView();

        binding.btnSelectFile.setOnClickListener(v -> {
            if (!isWebViewReady) {
                Toast.makeText(requireContext(), "解密引擎正在加载，请稍候", Toast.LENGTH_SHORT).show();
                return;
            }
            openFileChooser();
        });
    }

        
    private void initWebView() {
        WebSettings webSettings = binding.webview.getSettings();
        // 核心WebView配置，解决网页解析失败
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setLoadsImagesAutomatically(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        // 设置标准User-Agent，避免被网站拦截
        webSettings.setUserAgentString("Mozilla/5.0 (Linux; Android 14; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        binding.webview.setWebContentsDebuggingEnabled(true);
    
        binding.webview.addJavascriptInterface(new DecryptBridge(this), "AndroidDecryptBridge");
    
        binding.webview.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(@NonNull WebView view, @NonNull WebResourceRequest request, @NonNull WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    isWebViewReady = false;
                    binding.webview.setVisibility(View.GONE);
                    binding.errorTip.setVisibility(View.VISIBLE);
                    binding.tvStatus.setText(R.string.web_load_error);
                }
            }

            @Override
            public void onReceivedHttpError(@NonNull WebView view, @NonNull WebResourceRequest request, @NonNull WebResourceResponse errorResponse) {
                super.onReceivedHttpError(view, request, errorResponse);
                // 处理HTTP错误，避免页面空白
                if (request.isForMainFrame()) {
                    binding.tvStatus.setText("网页加载失败，HTTP错误码：" + errorResponse.getStatusCode());
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
                handler.proceed(); // 兼容SSL证书问题，避免加载失败
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // 仅加载目标站点，防止跳转导致的加载失败
                String url = request.getUrl().toString();
                if (url.startsWith(TARGET_URL) || url.startsWith("https://unlock-music.dev")) {
                    return false;
                }
                return true;
            }
    
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                isWebViewReady = true;
                binding.webview.setVisibility(View.INVISIBLE);
                binding.errorTip.setVisibility(View.GONE);
                binding.tvStatus.setText("解密引擎已就绪，可选择文件开始解密");
            }
        });

        binding.webview.setWebChromeClient(new WebChromeClient());
        binding.tvStatus.setText(R.string.status_preparing);
        binding.webview.loadUrl(TARGET_URL);
    }


    private void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        fileChooserLauncher.launch(intent);
    }

    private void startDecryptQueue() {
        if (pendingFileUris.isEmpty()) {
            requireActivity().runOnUiThread(() -> {
                binding.progressBar.setVisibility(View.GONE);
                binding.tvStatus.setText(String.format(getString(R.string.status_success), successCount.get(), failedCount.get()));
            });
            return;
        }

        Uri fileUri = pendingFileUris.remove(0);
        int currentIndex = totalFileCount - pendingFileUris.size();

        executor.execute(() -> {
            try {
                String fileName = getFileNameFromUri(fileUri);
                String mimeType = getMimeTypeFromUri(fileUri);
                byte[] fileData = readFileToByteArray(fileUri);
                String base64Data = android.util.Base64.encodeToString(fileData, android.util.Base64.NO_WRAP);

                requireActivity().runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.VISIBLE);
                    binding.tvStatus.setText(String.format(getString(R.string.status_decrypting), fileName, currentIndex, totalFileCount));
                });

                uploadFileToWebView(fileName, base64Data, mimeType);

            } catch (Exception e) {
                failedCount.incrementAndGet();
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "文件读取失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
                startDecryptQueue();
            }
        });
    }

    private void uploadFileToWebView(String fileName, String base64Data, String mimeType) {
        String jsCode = "(async () => {" +
                "  try {" +
                "    const byteCharacters = atob('" + base64Data + "');" +
                "    const byteNumbers = new Array(byteCharacters.length);" +
                "    for (let i = 0; i < byteCharacters.length; i++) {" +
                "      byteNumbers[i] = byteCharacters.charCodeAt(i);" +
                "    }" +
                "    const byteArray = new Uint8Array(byteNumbers);" +
                "    const blob = new Blob([byteArray], {type: '" + mimeType + "'});" +
                "    const file = new File([blob], '" + fileName + "', {type: '" + mimeType + "'});" +
                "" +
                "    let fileInput = document.querySelector('input[type=\"file\"]');" +
                "    if (!fileInput) {" +
                "      fileInput = document.createElement('input');" +
                "      fileInput.type = 'file';" +
                "      fileInput.multiple = true;" +
                "      fileInput.accept = 'audio/*';" +
                "      document.body.appendChild(fileInput);" +
                "    }" +
                "" +
                "    const dataTransfer = new DataTransfer();" +
                "    dataTransfer.items.add(file);" +
                "    fileInput.files = dataTransfer.files;" +
                "    fileInput.dispatchEvent(new Event('change', { bubbles: true }));" +
                "" +
                "    setTimeout(() => {" +
                "      const downloadLink = document.querySelector('a[download]');" +
                "      if (downloadLink && downloadLink.href.startsWith('blob:')) {" +
                "        fetch(downloadLink.href)" +
                "          .then(res => res.blob())" +
                "          .then(blob => {" +
                "            const reader = new FileReader();" +
                "            reader.onloadend = () => {" +
                "              const base64 = reader.result.split(',')[1];" +
                "              AndroidDecryptBridge.onDecryptSuccess(downloadLink.download, base64);" +
                "            };" +
                "            reader.readAsDataURL(blob);" +
                "          })" +
                "          .catch(e => AndroidDecryptBridge.onDecryptFailed('文件读取失败：' + e.message));" +
                "      } else {" +
                "        AndroidDecryptBridge.onDecryptFailed('未找到解密后的文件，可能解密失败');" +
                "      }" +
                "    }, 3000);" +
                "  } catch (e) {" +
                "    AndroidDecryptBridge.onDecryptFailed('JS执行失败：' + e.message);" +
                "  }" +
                "})();";

        requireActivity().runOnUiThread(() -> {
            binding.webview.evaluateJavascript(jsCode, null);
        });
    }

    @Override
    public void onDecryptSuccess(String fileName, byte[] fileData) {
        executor.execute(() -> {
            try {
                saveFileToDownloads(fileName, fileData);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failedCount.incrementAndGet();
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "文件保存失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
            requireActivity().runOnUiThread(this::startDecryptQueue);
        });
    }

    @Override
    public void onDecryptFailed(String errorMsg) {
        failedCount.incrementAndGet();
        requireActivity().runOnUiThread(() -> {
            Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show();
            startDecryptQueue();
        });
    }

    private String getFileNameFromUri(Uri uri) {
        String fileName = "unknown_audio";
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        fileName = cursor.getString(index);
                    }
                }
            }
        } else if (uri.getPath() != null) {
            fileName = new File(uri.getPath()).getName();
        }
        return fileName;
    }

    private String getMimeTypeFromUri(Uri uri) {
        String mimeType = requireContext().getContentResolver().getType(uri);
        if (mimeType == null) {
            String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
        }
        return mimeType != null ? mimeType : "application/octet-stream";
    }

    private byte[] readFileToByteArray(Uri uri) throws Exception {
        InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, len);
        }
        inputStream.close();
        return outputStream.toByteArray();
    }

    private void saveFileToDownloads(String fileName, byte[] fileData) throws Exception {
        File saveDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MusicDecrypter");
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }

        File saveFile = new File(saveDir, fileName);
        FileOutputStream fos = new FileOutputStream(saveFile);
        fos.write(fileData);
        fos.flush();
        fos.close();

        requireContext().sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(saveFile)));
    }

    @Override
    public void onDestroyView() {
        binding.webview.destroy();
        super.onDestroyView();
        binding = null;
        executor.shutdown();
    }
}
