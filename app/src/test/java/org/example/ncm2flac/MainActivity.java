package com.example.ncm2flac;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 显示简单的文本，证明应用能启动
        TextView textView = new TextView(this);
        textView.setText("NCM2FLAC Converter 已启动！");
        setContentView(textView);
    }
}
