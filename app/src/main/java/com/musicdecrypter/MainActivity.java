package com.musicdecrypter;

import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.musicdecrypter.utils.MusicDecryptUtils;
import com.musicdecrypter.utils.SpUtils;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MusicDecrypter_Main";
    private ViewPager2 viewPager;
    private BottomNavigationView bottomNav;

    // 引擎状态常量（简化，仅用于状态提示）
    public static final int ENGINE_STATE_IDLE = 0;
    public static final int ENGINE_STATE_READY = 2;
    private volatile int engineState = ENGINE_STATE_READY; // 纯Java解密无需初始化，直接就绪

    // 状态监听列表
    private final List<OnEngineStateChangeListener> stateListeners = new ArrayList<>();

    // 引擎状态监听接口（保持兼容）
    public interface OnEngineStateChangeListener {
        void onEngineStateChange(int state, String message);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initBottomNav();
        engineState = ENGINE_STATE_READY;
        notifyStateChange(ENGINE_STATE_READY, "解密引擎就绪");
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

    // 对外提供解密方法（替换原WebView解密）
    public void startDecrypt(String filePath, String fileName, DecryptCallback callback) {
        if (engineState != ENGINE_STATE_READY) {
            callback.onDecryptFailed("解密引擎未就绪");
            return;
        }

        // 子线程执行解密（避免阻塞主线程）
        new Thread(() -> {
            try {
                callback.onDecryptProgress(10, 100, 1); // 读取文件进度
                // 调用纯Java解密工具
                MusicDecryptUtils.DecryptResult result = MusicDecryptUtils.decrypt(filePath);
                
                callback.onDecryptProgress(50, 100, 3); // 解密中进度
                // 保存文件（使用设置页的保存路径）
                String saveDir = SpUtils.getSavePath(this);
                MusicDecryptUtils.saveDecryptResult(result, saveDir);
                
                callback.onDecryptProgress(100, 100, 4); // 保存完成
                callback.onDecryptSuccess(result.getOutFileName(), result.getDecryptedData());
            } catch (Exception e) {
                callback.onDecryptFailed("解密失败：" + e.getMessage());
            }
        }).start();
    }

    // 解密回调接口（保持与原桥接对象一致）
    public interface DecryptCallback {
        void onDecryptProgress(int current, int total, int step);
        void onDecryptSuccess(String fileName, byte[] fileData);
        void onDecryptFailed(String errorMsg);
    }

    // 状态监听相关方法（保持兼容）
    public void addEngineStateListener(OnEngineStateChangeListener listener) {
        if (!stateListeners.contains(listener)) {
            stateListeners.add(listener);
            listener.onEngineStateChange(engineState, getStateMessage());
        }
    }

    public void removeEngineStateListener(OnEngineStateChangeListener listener) {
        stateListeners.remove(listener);
    }

    private void notifyStateChange(int state, String message) {
        for (OnEngineStateChangeListener listener : stateListeners) {
            listener.onEngineStateChange(state, message);
        }
    }

    private String getStateMessage() {
        return engineState == ENGINE_STATE_READY ? "解密引擎就绪" : "就绪";
    }

    public int getEngineState() {
        return engineState;
    }
}
