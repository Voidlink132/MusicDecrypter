package com.musicdecrypter.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.musicdecrypter.MainActivity;
import com.musicdecrypter.R;
import com.musicdecrypter.utils.SpUtils;

import java.io.File;

public class DecryptFragment extends Fragment implements MainActivity.OnEngineStateChangeListener, MainActivity.DecryptCallback {

    private static final String TAG = "DecryptFragment";
    private LinearLayout llProgressArea;
    private TextView tvDecryptStep;
    private TextView tvProgressPercent;
    private ProgressBar decryptProgressBar;
    private MainActivity mainActivity;

    // 解密步骤文案（与SearchFragment保持一致）
    private final String[] stepTexts = {
            "就绪",
            "正在读取文件...",
            "正在初始化解密引擎...",
            "正在解密文件...",
            "正在保存文件...",
            "解密完成"
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_decrypt, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 绑定控件（与布局文件保持一致）
        bindViews(view);
    }

    private void bindViews(View view) {
        llProgressArea = view.findViewById(R.id.ll_progress_area);
        tvDecryptStep = view.findViewById(R.id.tv_decrypt_step);
        tvProgressPercent = view.findViewById(R.id.tv_progress_percent);
        decryptProgressBar = view.findViewById(R.id.decrypt_progress_bar);
    }

    @Override
    public void onStart() {
        super.onStart();
        // 绑定MainActivity，监听引擎状态
        if (getActivity() instanceof MainActivity) {
            mainActivity = (MainActivity) getActivity();
            mainActivity.addEngineStateListener(this);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        // 移除监听，避免内存泄漏
        if (mainActivity != null) {
            mainActivity.removeEngineStateListener(this);
        }
    }

    // 对外提供解密触发方法（供外部调用，如文件选择后跳转解密）
    public void startDecrypt(String filePath, String fileName) {
        if (mainActivity == null || mainActivity.getEngineState() != MainActivity.ENGINE_STATE_READY) {
            Toast.makeText(requireContext(), "解密引擎未就绪，请稍候重试", Toast.LENGTH_SHORT).show();
            return;
        }

        // 显示解密进度UI
        llProgressArea.setVisibility(View.VISIBLE);
        tvDecryptStep.setText(String.format("正在解密：%s", fileName));
        tvProgressPercent.setText("0%");
        decryptProgressBar.setProgress(0);

        // 调用MainActivity的纯Java解密方法
        mainActivity.startDecrypt(filePath, fileName, this);
    }

    // ==================== 适配MainActivity的回调接口 ====================
    @Override
    public void onEngineStateChange(int state, String message) {
        if (!isAdded() || getContext() == null) return;
        requireActivity().runOnUiThread(() -> {
            switch (state) {
                case MainActivity.ENGINE_STATE_READY:
                    llProgressArea.setVisibility(View.GONE);
                    decryptProgressBar.setIndeterminate(false);
                    decryptProgressBar.setProgress(0);
                    tvProgressPercent.setText("0%");
                    tvDecryptStep.setText("解密引擎就绪");
                    break;
                case MainActivity.ENGINE_STATE_IDLE:
                    llProgressArea.setVisibility(View.VISIBLE);
                    tvDecryptStep.setText(message);
                    tvProgressPercent.setVisibility(View.GONE);
                    decryptProgressBar.setIndeterminate(true);
                    break;
                default:
                    llProgressArea.setVisibility(View.VISIBLE);
                    tvDecryptStep.setText(message);
                    tvProgressPercent.setVisibility(View.GONE);
                    decryptProgressBar.setIndeterminate(false);
            }
        });
    }

    @Override
    public void onDecryptProgress(int current, int total, int step) {
        if (!isAdded() || getContext() == null) return;
        requireActivity().runOnUiThread(() -> {
            decryptProgressBar.setProgress(current);
            tvProgressPercent.setText(current + "%");
            if (step >= 0 && step < stepTexts.length) {
                tvDecryptStep.setText(stepTexts[step]);
            }
        });
    }

    @Override
    public void onDecryptSuccess(String fileName, byte[] fileData) {
        if (!isAdded() || getContext() == null) return;
        requireActivity().runOnUiThread(() -> {
            llProgressArea.setVisibility(View.GONE);
            String savePath = SpUtils.getSavePath(requireContext()) + File.separator + fileName;
            Toast.makeText(requireContext(), "解密成功！已保存到：\n" + savePath, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onDecryptFailed(String errorMsg) {
        if (!isAdded() || getContext() == null) return;
        requireActivity().runOnUiThread(() -> {
            llProgressArea.setVisibility(View.GONE);
            Toast.makeText(requireContext(), "解密失败：" + errorMsg, Toast.LENGTH_SHORT).show();
        });
    }
}
