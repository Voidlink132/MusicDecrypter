package com.ncmconverter.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import java.lang.reflect.Method;

public class PermTool {
    // 核心修复：替换Build.VERSION_CODES.R为数字30（兼容Termux的android.jar）
    private static final int ANDROID_R = 30;
    // 核心修复：直接定义权限Action字符串（避免Settings类常量未定义）
    private static final String ACTION_MANAGE_ALL_FILES = "android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION";

    // 检查存储权限（启动时调用）
    public static void checkStoragePerm(final Context context) {
        if (!hasStoragePerm(context)) {
            new AlertDialog.Builder(context)
                    .setTitle("需要文件访问权限")
                    .setMessage("为了正常转换NCM和下载LRC，需要开启所有文件访问权限")
                    .setPositiveButton("去设置", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            goSetPerm(context);
                        }
                    })
                    .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Toast.makeText(context, "无权限将无法使用功能", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setCancelable(false)
                    .show();
        }
    }

    // 判断是否有存储权限（全局调用，修复反射API报错）
    public static boolean hasStoragePerm(Context context) {
        if (Build.VERSION.SDK_INT >= ANDROID_R) {
            // Android11+/15+ 用反射判断（修复方法调用报错）
            try {
                Method method = Environment.class.getMethod("isExternalStorageManager");
                return (Boolean) method.invoke(null);
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        } else {
            // 低版本判断普通权限（兼容）
            return android.content.pm.PackageManager.PERMISSION_GRANTED ==
                    context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) &&
                    android.content.pm.PackageManager.PERMISSION_GRANTED ==
                    context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
    }

    // 跳转到权限设置页面（设置页调用，修复Uri报错）
    public static void goSetPerm(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= ANDROID_R) {
                Intent intent = new Intent(ACTION_MANAGE_ALL_FILES);
                intent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
                context.startActivity(intent);
            } else {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(android.net.Uri.fromParts("package", context.getPackageName(), null));
                context.startActivity(intent);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "跳转到设置失败，请手动开启", Toast.LENGTH_SHORT).show();
        }
    }
}
