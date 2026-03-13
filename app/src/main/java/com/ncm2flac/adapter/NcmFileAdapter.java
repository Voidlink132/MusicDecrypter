package com.ncm2flac.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ncm2flac.R;
import com.ncm2flac.core.LyricDownloader;
import com.ncm2flac.core.MetadataHandler;
import com.ncm2flac.core.NcmDecryptor;
import com.ncm2flac.utils.FileUtils;
import com.ncm2flac.utils.SPUtils;

import java.io.File;
import java.util.List;

public class NcmFileAdapter extends RecyclerView.Adapter<NcmFileAdapter.ViewHolder> {
    private final Context context;
    private final List<File> ncmFileList;
    private final OnConvertListener listener;

    // 转换状态回调接口
    public interface OnConvertListener {
        void onConvertStart();
        void onConvertFinish(boolean success, String msg);
    }

    public NcmFileAdapter(Context context, List<File> ncmFileList, OnConvertListener listener) {
        this.context = context;
        this.ncmFileList = ncmFileList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_ncm_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        File ncmFile = ncmFileList.get(position);
        // 绑定文件名和大小
        holder.tvFileName.setText(ncmFile.getName());
        holder.tvFileSize.setText(FileUtils.formatFileSize(ncmFile.length()));

        // 转换按钮点击事件
        holder.btnConvert.setOnClickListener(v -> {
            if (listener != null) listener.onConvertStart();
            // 子线程执行解密转换，避免ANR
            new Thread(() -> {
                try {
                    // 1. 读取NCM文件
                    byte[] ncmData = FileUtils.readFileToBytes(ncmFile);
                    NcmDecryptor decryptor = new NcmDecryptor(ncmData);

                    // 2. 解密校验（对齐ncmc标准）
                    if (!decryptor.decrypt()) {
                        if (listener != null) {
                            listener.onConvertFinish(false, "解密失败，不是有效的NCM文件");
                        }
                        return;
                    }

                    // 3. 生成输出文件
                    String outputFileName = FileUtils.replaceFileExtension(ncmFile.getName(), decryptor.getAudioFormat());
                    File outputFile = new File(FileUtils.getOutputDir(context), outputFileName);
                    // 写入无损音频流
                    FileUtils.writeBytesToFile(decryptor.getAudioRawData(), outputFile);

                    // 4. 写入歌曲元数据
                    MetadataHandler.writeMetadata(outputFile, decryptor.getMetadata());

                    // 5. 自动下载LRC歌词（开关控制）
                    if (SPUtils.getInstance(context).isLrcEnable()) {
                        String lrcFileName = FileUtils.replaceFileExtension(ncmFile.getName(), "lrc");
                        File lrcFile = new File(FileUtils.getOutputDir(context), lrcFileName);
                        LyricDownloader.downloadAndSaveLrc(decryptor.getSongId(), lrcFile);
                    }

                    // 6. 回调成功结果
                    if (listener != null) {
                        listener.onConvertFinish(true, "转换成功！文件已保存到：" + outputFile.getAbsolutePath());
                    }

                } catch (Exception e) {
                    // 回调失败结果
                    if (listener != null) {
                        listener.onConvertFinish(false, "转换失败：" + e.getMessage());
                    }
                    e.printStackTrace();
                }
            }).start();
        });
    }

    @Override
    public int getItemCount() {
        return ncmFileList.size();
    }

    // ViewHolder内部类
    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvFileName, tvFileSize;
        Button btnConvert;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFileName = itemView.findViewById(R.id.tv_file_name);
            tvFileSize = itemView.findViewById(R.id.tv_file_size);
            btnConvert = itemView.findViewById(R.id.btn_convert);
        }
    }
}
