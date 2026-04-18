package com.musicdecrypter.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.musicdecrypter.R;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SettingsFragment extends Fragment {

    private TextView tvSavePath;
    private TextView tvPermissionStatus;
    private TextView tvVersion;
    
    private TextView tvStatusNetease;
    private TextView tvStatusQQ;
    
    private SharedPreferences sp;
    private Handler handler = new Handler(Looper.getMainLooper());
    private int demoProgress = 0;
    private Runnable demoRunnable;
    private View rootView;
    private final OkHttpClient client = new OkHttpClient();

    private final ActivityResultLauncher<Intent> dirChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        Toast.makeText(requireContext(), "已选择新目录", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_settings, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        sp = requireContext().getSharedPreferences("config", Context.MODE_PRIVATE);

        // 1. 初始化通用视图
        tvSavePath = view.findViewById(R.id.tv_save_path);
        tvPermissionStatus = view.findViewById(R.id.tv_permission_status);
        tvVersion = view.findViewById(R.id.tv_version);
        tvStatusNetease = view.findViewById(R.id.tv_status_netease);
        tvStatusQQ = view.findViewById(R.id.tv_status_qq);
        
        // 2. 账号登录与检测
        view.findViewById(R.id.btn_login_netease).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            intent.putExtra("platform", "netease");
            intent.putExtra("url", "https://music.163.com/m/login");
            startActivity(intent);
        });

        view.findViewById(R.id.btn_detect_netease).setOnClickListener(v -> detectLoginStatus("netease"));

        view.findViewById(R.id.btn_login_qq).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            intent.putExtra("platform", "qq");
            intent.putExtra("url", "https://i.y.qq.com/n2/m/login/login.html"); // 使用专门的移动端登录页
            startActivity(intent);
        });

        view.findViewById(R.id.btn_detect_qq).setOnClickListener(v -> detectLoginStatus("qq"));

        // 3. 外观设置
        TextView tvThemeColor = view.findViewById(R.id.tv_theme_color);
        TextView tvBackgroundStyle = view.findViewById(R.id.tv_background_style);

        tvThemeColor.setText(sp.getString("theme_color", "默认粉色"));
        view.findViewById(R.id.btn_theme_color).setOnClickListener(v -> {
            String[] colors = {"默认粉色", "天空蓝", "活力橙", "极简黑", "深邃紫"};
            new AlertDialog.Builder(requireContext())
                    .setTitle("选择主题颜色")
                    .setItems(colors, (dialog, which) -> {
                        String selected = colors[which];
                        sp.edit().putString("theme_color", selected).apply();
                        tvThemeColor.setText(selected);
                        applyAppearance();
                        Toast.makeText(getContext(), "主题颜色已更改", Toast.LENGTH_SHORT).show();
                    }).show();
        });

        tvBackgroundStyle.setText(sp.getString("background_style", "默认浅灰"));
        view.findViewById(R.id.btn_background_style).setOnClickListener(v -> {
            String[] styles = {"默认浅灰", "纯白", "暗夜黑", "羊皮纸", "毛玻璃"};
            new AlertDialog.Builder(requireContext())
                    .setTitle("选择背景样式")
                    .setItems(styles, (dialog, which) -> {
                        String selected = styles[which];
                        sp.edit().putString("background_style", selected).apply();
                        tvBackgroundStyle.setText(selected);
                        applyAppearance();
                        Toast.makeText(getContext(), "背景样式已更改", Toast.LENGTH_SHORT).show();
                    }).show();
        });

        // 4. 歌词设置功能
        SwitchMaterial switchFetchLyric = view.findViewById(R.id.switch_fetch_lyric);
        View layoutLyricSettings = view.findViewById(R.id.layout_lyric_settings);
        TextView tvLyricEncoding = view.findViewById(R.id.tv_lyric_encoding);
        TextView tvLyricFormat = view.findViewById(R.id.tv_lyric_format);
        SwitchMaterial switchBilingualLyric = view.findViewById(R.id.switch_bilingual_lyric);
        View layoutBilingualOptions = view.findViewById(R.id.layout_bilingual_options);
        TextView tvBilingualType = view.findViewById(R.id.tv_bilingual_type);
        View layoutCombineSymbol = view.findViewById(R.id.layout_combine_symbol);
        EditText etCombineSymbol = view.findViewById(R.id.et_combine_symbol);

        // 歌词总开关
        boolean fetchLyric = sp.getBoolean("fetch_lyric", false);
        switchFetchLyric.setChecked(fetchLyric);
        layoutLyricSettings.setVisibility(fetchLyric ? View.VISIBLE : View.GONE);
        switchFetchLyric.setOnCheckedChangeListener((btn, isChecked) -> {
            sp.edit().putBoolean("fetch_lyric", isChecked).apply();
            layoutLyricSettings.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // 编码
        tvLyricEncoding.setText(sp.getString("lyric_encoding", "UTF-8"));
        view.findViewById(R.id.btn_lyric_encoding).setOnClickListener(v -> {
            String[] encodings = {"UTF-8", "UTF-16 LE"};
            new AlertDialog.Builder(requireContext())
                    .setTitle("选择歌词输出编码")
                    .setItems(encodings, (dialog, which) -> {
                        String selected = encodings[which];
                        sp.edit().putString("lyric_encoding", selected).apply();
                        tvLyricEncoding.setText(selected);
                    }).show();
        });

        // 格式
        tvLyricFormat.setText(sp.getString("lyric_format", "LRC"));
        view.findViewById(R.id.btn_lyric_format).setOnClickListener(v -> {
            String[] formats = {"LRC", "SRT"};
            new AlertDialog.Builder(requireContext())
                    .setTitle("选择歌词输出格式")
                    .setItems(formats, (dialog, which) -> {
                        String selected = formats[which];
                        sp.edit().putString("lyric_format", selected).apply();
                        tvLyricFormat.setText(selected);
                    }).show();
        });

        // 双语开关
        boolean bilingual = sp.getBoolean("bilingual_lyric", false);
        switchBilingualLyric.setChecked(bilingual);
        layoutBilingualOptions.setVisibility(bilingual ? View.VISIBLE : View.GONE);
        switchBilingualLyric.setOnCheckedChangeListener((btn, isChecked) -> {
            sp.edit().putBoolean("bilingual_lyric", isChecked).apply();
            layoutBilingualOptions.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // 双语展示方式
        String bilingualType = sp.getString("bilingual_type", "合并");
        tvBilingualType.setText(bilingualType);
        layoutCombineSymbol.setVisibility("合并".equals(bilingualType) ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.btn_bilingual_type).setOnClickListener(v -> {
            String[] types = {"合并", "交错", "独立"};
            new AlertDialog.Builder(requireContext())
                    .setTitle("双语展示方式")
                    .setItems(types, (dialog, which) -> {
                        String selected = types[which];
                        sp.edit().putString("bilingual_type", selected).apply();
                        tvBilingualType.setText(selected);
                        layoutCombineSymbol.setVisibility("合并".equals(selected) ? View.VISIBLE : View.GONE);
                    }).show();
        });

        // 合并符
        etCombineSymbol.setText(sp.getString("combine_symbol", "/"));
        etCombineSymbol.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                sp.edit().putString("combine_symbol", s.toString()).apply();
            }
        });

        // 5. 开源与关于
        view.findViewById(R.id.tv_about).setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                .setTitle("关于本工具")
                .setMessage("MusicDecrypter 是一款专业的音乐解密辅助工具。\n\n旨在让音乐回归本质，实现解密自由。")
                .setPositiveButton("知道了", null)
                .show());

        // 6. 系统设置
        view.findViewById(R.id.layout_permission).setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    try {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
                        startActivity(intent);
                    } catch (Exception e) {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                        startActivity(intent);
                    }
                } else {
                    Toast.makeText(getContext(), "已获得所有文件访问权限", Toast.LENGTH_SHORT).show();
                }
            }
        });

        view.findViewById(R.id.layout_save_path).setOnClickListener(v -> openMusicDirectory(tvSavePath.getText().toString()));

        SwitchMaterial switchShowOthers = view.findViewById(R.id.switch_show_others);
        switchShowOthers.setChecked(sp.getBoolean("show_others", false));
        switchShowOthers.setOnCheckedChangeListener((btn, isChecked) -> {
            sp.edit().putBoolean("show_others", isChecked).apply();
        });

        // 7. 基础信息初始化
        String defaultPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath() + "/MusicDecrypter";
        tvSavePath.setText(defaultPath);

        try {
            PackageInfo pInfo = requireContext().getPackageManager().getPackageInfo(requireContext().getPackageName(), 0);
            tvVersion.setText(pInfo.versionName);
        } catch (PackageManager.NameNotFoundException e) {
            tvVersion.setText("2.0");
        }

        // 8. 界面演示动画
        ProgressBar pbDemo = view.findViewById(R.id.pb_demo);
        TextView tvDemoProgressView = view.findViewById(R.id.tv_demo_progress);
        demoRunnable = new Runnable() {
            @Override
            public void run() {
                demoProgress++;
                if (demoProgress > 100) demoProgress = 0;
                if (pbDemo != null) pbDemo.setProgress(demoProgress);
                if (tvDemoProgressView != null) tvDemoProgressView.setText(demoProgress + "%");
                handler.postDelayed(this, 50);
            }
        };
        handler.post(demoRunnable);

        applyAppearance();
        updatePermissionStatus();
        updateLoginStatus();
    }

    private void detectLoginStatus(String platform) {
        String cookie = sp.getString("cookie_" + platform, "");
        if (cookie.isEmpty()) {
            Toast.makeText(getContext(), "尚未获取到 Cookie，请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(getContext(), "正在检测 " + platform + " 登录状态...", Toast.LENGTH_SHORT).show();

        if ("netease".equals(platform)) {
            Request request = new Request.Builder()
                    .url("https://music.163.com/api/v1/user/info")
                    .addHeader("Cookie", cookie)
                    .build();
            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) { showDetectResult(false, "网络请求失败"); }
                @Override public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String body = response.body().string();
                        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                        if (json.has("code") && json.get("code").getAsInt() == 200) {
                            showDetectResult(true, "网易云已成功登录");
                        } else {
                            showDetectResult(false, "Cookie 已失效，请重新登录");
                        }
                    } catch (Exception e) { showDetectResult(false, "解析失败"); }
                }
            });
        } else if ("qq".equals(platform)) {
            Request request = new Request.Builder()
                    .url("https://u.y.qq.com/cgi-bin/musicu.fcg?data=%7B%22req_0%22%3A%7B%22module%22%3A%22vkey.GetVkeyServer%22%2C%22method%22%3A%22CgiGetVkey%22%2C%22param%22%3A%7B%22guid%22%3A%2212345678%22%2C%22songmid%22%3A%5B%22003aXpNo4elS13%22%5D%2C%22songtype%22%3A%5B0%5D%2C%22uin%22%3A%220%22%2C%22loginflag%22%3A1%2C%22platform%22%3A%2220%22%7D%7D%7D")
                    .addHeader("Cookie", cookie)
                    .build();
            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) { showDetectResult(false, "网络请求失败"); }
                @Override public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String body = response.body().string();
                        if (body.contains("uin")) {
                            showDetectResult(true, "QQ 音乐已成功登录");
                        } else {
                            showDetectResult(false, "Cookie 无效或已过期");
                        }
                    } catch (Exception e) { showDetectResult(false, "解析失败"); }
                }
            });
        }
    }

    private void showDetectResult(boolean success, String msg) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle(success ? "检测成功" : "检测失败")
                    .setMessage(msg)
                    .setPositiveButton("确定", null)
                    .show();
            updateLoginStatus();
        });
    }

    private void applyAppearance() {
        if (rootView == null) return;
        
        // 应用背景样式
        String bgStyle = sp.getString("background_style", "默认浅灰");
        int bgColor;
        switch (bgStyle) {
            case "纯白": bgColor = Color.WHITE; break;
            case "暗夜黑": bgColor = Color.parseColor("#121212"); break;
            case "羊皮纸": bgColor = Color.parseColor("#F5F5DC"); break;
            case "毛玻璃": bgColor = Color.parseColor("#E0E0E0"); break;
            default: bgColor = Color.parseColor("#F5F5F7"); break;
        }
        rootView.setBackgroundColor(bgColor);

        // 应用主题色
        String theme = sp.getString("theme_color", "默认粉色");
        int themeColor;
        switch (theme) {
            case "天空蓝": themeColor = Color.parseColor("#2196F3"); break;
            case "活力橙": themeColor = Color.parseColor("#FF9800"); break;
            case "极简黑": themeColor = Color.parseColor("#333333"); break;
            case "深邃紫": themeColor = Color.parseColor("#673AB7"); break;
            default: themeColor = Color.parseColor("#FF4081"); break;
        }

        // 1. 修改进度文字演示颜色
        TextView tvDemoProgress = rootView.findViewById(R.id.tv_demo_progress);
        if (tvDemoProgress != null) tvDemoProgress.setTextColor(themeColor);

        // 2. 修改 ProgressBar 的颜色
        ProgressBar pbDemo = rootView.findViewById(R.id.pb_demo);
        if (pbDemo != null) {
            pbDemo.setProgressTintList(ColorStateList.valueOf(themeColor));
        }

        // 3. 批量修改界面中所有原本为粉色的文字（Section 标题等）
        List<TextView> pinkTextViews = findTextViewsByColor(rootView, Color.parseColor("#FF4081"));
        for (TextView tv : pinkTextViews) {
            tv.setTextColor(themeColor);
        }

        // 4. 修改检测按钮颜色
        TextView d1 = rootView.findViewById(R.id.btn_detect_netease);
        TextView d2 = rootView.findViewById(R.id.btn_detect_qq);
        if (d1 != null) d1.setTextColor(themeColor);
        if (d2 != null) d2.setTextColor(themeColor);

        // 5. 批量修改 Switch 的颜色
        int[][] states = new int[][] {
            new int[] {-android.R.attr.state_checked},
            new int[] {android.R.attr.state_checked}
        };
        int[] colors = new int[] { Color.LTGRAY, themeColor };
        ColorStateList switchColors = new ColorStateList(states, colors);

        SwitchMaterial s1 = rootView.findViewById(R.id.switch_fetch_lyric);
        SwitchMaterial s2 = rootView.findViewById(R.id.switch_bilingual_lyric);
        SwitchMaterial s3 = rootView.findViewById(R.id.switch_show_others);
        if (s1 != null) s1.setThumbTintList(switchColors);
        if (s2 != null) s2.setThumbTintList(switchColors);
        if (s3 != null) s3.setThumbTintList(switchColors);
        
        // 6. 修改图标 Tint
        int[] layoutIds = {R.id.btn_theme_color, R.id.btn_background_style, R.id.btn_login_netease, R.id.btn_login_qq};
        for (int id : layoutIds) {
            View v = rootView.findViewById(id);
            if (v instanceof ViewGroup) {
                View icon = ((ViewGroup) v).getChildAt(0);
                if (icon instanceof ImageView) ((ImageView) icon).setColorFilter(themeColor, PorterDuff.Mode.SRC_IN);
            }
        }
        
        TextView tvBilingualType = rootView.findViewById(R.id.tv_bilingual_type);
        if (tvBilingualType != null) tvBilingualType.setTextColor(themeColor);
    }

    private List<TextView> findTextViewsByColor(View view, int targetColor) {
        List<TextView> result = new ArrayList<>();
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                result.addAll(findTextViewsByColor(vg.getChildAt(i), targetColor));
            }
        } else if (view instanceof TextView) {
            TextView tv = (TextView) view;
            if (tv.getCurrentTextColor() == targetColor) {
                result.add(tv);
            }
        }
        return result;
    }

    private void openMusicDirectory(String path) {
        File file = new File(path);
        if (!file.exists()) file.mkdirs();
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3AMusic%2FMusicDecrypter");
            intent.setDataAndType(uri, "vnd.android.cursor.dir/document");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);

            String[] targetPackages = {"com.google.android.documentsui", "com.android.documentsui", "com.android.fileexplorer", "com.miui.explorer", "com.coloros.filemanager", "com.huawei.hidisk"};
            boolean success = false;
            for (String pkg : targetPackages) {
                try {
                    intent.setPackage(pkg);
                    startActivity(intent);
                    success = true;
                    break;
                } catch (Exception ignored) {}
            }
            if (!success) {
                intent.setPackage(null);
                startActivity(Intent.createChooser(intent, "使用文件管理打开"));
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), "无法跳转到文件夹", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        applyAppearance();
        updatePermissionStatus();
        updateLoginStatus();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (demoRunnable != null) handler.removeCallbacks(demoRunnable);
    }

    private void updatePermissionStatus() {
        if (tvPermissionStatus == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            boolean hasPermission = Environment.isExternalStorageManager();
            tvPermissionStatus.setText(hasPermission ? "已授权" : "未授权");
            tvPermissionStatus.setTextColor(hasPermission ? 0xFF4CAF50 : 0xFFF44336);
        } else {
            tvPermissionStatus.setText("系统版本无需此项");
            tvPermissionStatus.setTextColor(0xFF999999);
        }
    }

    private void updateLoginStatus() {
        if (tvStatusNetease == null || tvStatusQQ == null) return;
        String cookieN = sp.getString("cookie_netease", "");
        tvStatusNetease.setText(cookieN.isEmpty() ? "未登录" : "Cookie 已获取 (建议点击检测)");
        tvStatusNetease.setTextColor(cookieN.isEmpty() ? 0xFF999999 : 0xFF4CAF50);
        
        String cookieQ = sp.getString("cookie_qq", "");
        tvStatusQQ.setText(cookieQ.isEmpty() ? "未登录" : "Cookie 已获取 (建议点击检测)");
        tvStatusQQ.setTextColor(cookieQ.isEmpty() ? 0xFF999999 : 0xFF4CAF50);
    }
}
