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
        double fileSizeMb = ncmFile.length() / 1024.0 / 1024.0;
        holder.tvFileSize.setText(String.format(Locale.getDefault(), "%.2f MB", fileSizeMb));

        // 重置状态
        holder.btnConvert.setVisibility(View.VISIBLE);
        holder.btnConvert.setText("转换");
        holder.btnConvert.setBackgroundColor(0xFF00BCD4);
        holder.btnConvert.setEnabled(true);
        holder.progressConvert.setVisibility(View.GONE);
        holder.tvProgress.setVisibility(View.GONE);

        holder.btnConvert.setOnClickListener(v -> {
            holder.btnConvert.setVisibility(View.GONE);
            holder.progressConvert.setVisibility(View.VISIBLE);
            holder.tvProgress.setVisibility(View.VISIBLE);
            holder.progressConvert.setProgress(0);
            holder.tvProgress.setText("0%");

            convertExecutor.submit(() -> {
                try {
                    NcmDecryptCore decryptCore = new NcmDecryptCore(ncmFile, savePath);
                    decryptCore.startDecrypt(new OnConvertListener() {
                        @Override
                        public void onStart() {
                            ((android.app.Activity) context).runOnUiThread(() -> {
                                holder.progressConvert.setProgress(0);
                                holder.tvProgress.setText("0%");
                            });
                        }

                        @Override
                        public void onProgress(int progress) {
                            ((android.app.Activity) context).runOnUiThread(() -> {
                                holder.progressConvert.setProgress(progress);
                                holder.tvProgress.setText(progress + "%");
                            });
                        }

                        @Override
                        public void onSuccess(File outputFile) {
                            ((android.app.Activity) context).runOnUiThread(() -> {
                                holder.btnConvert.setVisibility(View.VISIBLE);
                                holder.btnConvert.setText("完成");
                                holder.btnConvert.setBackgroundColor(0xFF4CAF50);
                                holder.btnConvert.setEnabled(false);
                                holder.progressConvert.setVisibility(View.GONE);
                                holder.tvProgress.setVisibility(View.GONE);
                                android.widget.Toast.makeText(context, "转换成功！路径: " + outputFile.getAbsolutePath(), android.widget.Toast.LENGTH_LONG).show();
                            });
                        }

                        @Override
                        public void onFail(String errorMsg) {
                            ((android.app.Activity) context).runOnUiThread(() -> {
                                holder.btnConvert.setVisibility(View.VISIBLE);
                                holder.btnConvert.setText("重试");
                                holder.progressConvert.setVisibility(View.GONE);
                                holder.tvProgress.setVisibility(View.GONE);
                                android.widget.Toast.makeText(context, "转换失败: " + errorMsg, android.widget.Toast.LENGTH_LONG).show();
                            });
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        holder.btnConvert.setVisibility(View.VISIBLE);
                        holder.btnConvert.setText("重试");
                        holder.progressConvert.setVisibility(View.GONE);
                        holder.tvProgress.setVisibility(View.GONE);
                        android.widget.Toast.makeText(context, "异常: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                    });
                }
            });
        });
    }

    @Override
    public int getItemCount() {
        return fileList.size();
    }

    public void release() {
        if (!convertExecutor.isShutdown()) convertExecutor.shutdownNow();
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
