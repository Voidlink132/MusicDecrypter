package com.ncm2flac;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class SettingFragment extends Fragment {

    // 必需的空构造方法，Android系统要求
    public SettingFragment() {
    }

    // 静态创建实例方法，标准Fragment写法
    public static SettingFragment newInstance() {
        return new SettingFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 加载设置页布局
        return inflater.inflate(R.layout.fragment_setting, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 安全获取MainActivity实例，彻底解决类型转换错误
        MainActivity mainActivity = getActivity() instanceof MainActivity ? (MainActivity) getActivity() : null;
        if (mainActivity != null) {
            // 调用MainActivity的方法
            mainActivity.updateBackground();
        } else {
            Toast.makeText(getContext(), "页面加载异常", Toast.LENGTH_SHORT).show();
        }

        // 此处可添加你的设置页其他逻辑
    }
}
