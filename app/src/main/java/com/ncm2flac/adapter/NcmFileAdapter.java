package com.ncm2flac.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ncm2flac.OnConvertListener;
import com.ncm2flac.R;
import com.ncm2flac.core.NcmDecryptCore;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NcmFileAdapter extends RecyclerView.Adapter<NcmFileAdapter.ViewHolder> {

    private final Context context;
    private final List<File> fileList;
    private final String savePath;
    // 单线程池，避免多文件同时转换导致内存溢出
    private final ExecutorService convertExecutor = Executors.newSingleThreadExecutor();

    public NcmFileAdapter(Context context, List<File> fileList, String savePath) {
        this.context = context;
        this.fileList = fileList;
        this.savePath = savePath;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_ncm_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        File ncmFile = fileList.get(position);
        holder.tvFileName.setText(ncmFile.getName());
        // 计算文件大小，显示MB
        double fileSizeMb = ncmFile.length() / 1024.0 / 1024.0;
        holder.tvFileSize.setText(String.format(Locale.getDefault(), "%.2f MB", fileSizeMb));

        // 重置控件状态
        holder.btnConvert.setVisibility(View.VISIBLE);
        holder.progressConvert.setVisibility(View.GONE);
        holder.tvProgress.setVisibility(View.GONE);

        // 转换按钮点击事件
        holder.btnConvert.setOnClickListener(v -> {
            // 开始转换，更新UI
            holder.btnConvert.setVisibility(View.GONE);
            holder.progressConvert.setVisibility(View.VISIBLE);
            holder.tvProgress.setVisibility(View.VISIBLE);
            holder.progressConvert.setProgress(0);
            holder.tvProgress.setText("0%");

            // 提交转换任务到线程池，彻底避免主线程ANR闪退
            convertExecutor.submit(() -> {
                try {
                    // 初始化解密核心类
                    NcmDecryptCore decryptCore = new NcmDecryptCore(ncmFile, savePath);
                    
                    // 解密转换，带进度回调
                    decryptCore.startDecrypt(new OnConvertListener() {
                        @Override
                        public void onStart() {
                            // 主线程更新UI
                            ((android.app.Activity) context).runOnUiThread(() -> {
                                holder.progressConvert.setProgress(0);
                                holder.tvProgress.setText("0%");
                            });
                        }

                        @Override
                        public void onProgress(int progress) {
                            // 主线程更新进度
                            ((android.app.Activity) context).runOnUiThread(() -> {
                                holder.progressConvert.setProgress(progress);
                                holder.tvProgress.setText(progress + "%");
                            });
                        }

                        @Override
                        public void onSuccess(File outputFile) {
                            // 主线程更新成功状态
                            ((android.app.Activity) context).runOnUiThread(() -> {
                                holder.btnConvert.setVisibility(View.VISIBLE);
                                holder.btnConvert.setText("转换完成");
                                holder.btnConvert.setBackgroundColor(0xFF4CAF50);
                                holder.btnConvert.setEnabled(false);
                                holder.progressConvert.setVisibility(View.GONE);
                                holder.tvProgress.setVisibility(View.GONE);
                                android.widget.Toast.makeText(context, "转换成功！文件已保存到：" + outputFile.getAbsolutePath(), android.widget.Toast.LENGTH_LONG).show();
                            });
                        }

                        @Override
                        public void onFail(String errorMsg) {
                            // 主线程更新失败状态
                            ((android.app.Activity) context).runOnUiThread(() -> {
                                holder.btnConvert.setVisibility(View.VISIBLE);
                                holder.btnConvert.setText("重试");
                                holder.progressConvert.setVisibility(View.GONE);
                                holder.tvProgress.setVisibility(View.GONE);
                                android.widget.Toast.makeText(context, "转换失败：" + errorMsg, android.widget.Toast.LENGTH_LONG).show();
                            });
                        }
                    });

                } catch (Exception e) {
                    // 全局异常捕获，彻底避免闪退
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        holder.btnConvert.setVisibility(View.VISIBLE);
                        holder.btnConvert.setText("重试");
                        holder.progressConvert.setVisibility(View.GONE);
                        holder.tvProgress.setVisibility(View.GONE);
                        android.widget.Toast.makeText(context, "转换异常：" + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                    });
                }
            });
        });
    }

    @Override
    public int getItemCount() {
        return fileList.size();
    }

    // 释放线程池，避免内存泄漏
    public void release() {
        if (!convertExecutor.isShutdown()) {
            convertExecutor.shutdownNow();
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvFileName, tvFileSize, tvProgress;
        Button btnConvert;
        ProgressBar progressConvert;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFileName = itemView.findViewById(R.id.tv_file_name);
            tvFileSize = itemView.findViewById(R.id.tv_file_size);
            tvProgress = itemView.findViewById(R.id.tv_progress);
            btnConvert = itemView.findViewById(R.id.btn_convert);
            progressConvert = itemView.findViewById(R.id.progress_convert);
        }
    }
}
