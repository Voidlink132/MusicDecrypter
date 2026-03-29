// 在 fragment_decrypt.xml 里添加下载按钮
<com.google.android.material.button.MaterialButton
    android:id="@+id/btn_download"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="下载解密文件"
    android:visibility="gone"/>

// 在 DecryptFragment.java 里绑定按钮
binding.btnDownload.setOnClickListener(v -> {
    // 跳转到下载目录
    Intent intent = new Intent(Intent.ACTION_VIEW);
    Uri uri = Uri.parse(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/MusicDecrypter/");
    intent.setDataAndType(uri, "resource/folder");
    startActivity(Intent.createChooser(intent, "打开下载目录"));
});

// 在解密成功后显示按钮
@Override
public void onDecryptSuccess(String fileName, byte[] fileData) {
    // ... 原有保存逻辑
    requireActivity().runOnUiThread(() -> {
        binding.btnDownload.setVisibility(View.VISIBLE);
        binding.tvStatus.setText("解密成功，文件已保存到下载/MusicDecrypter/");
    });
}
