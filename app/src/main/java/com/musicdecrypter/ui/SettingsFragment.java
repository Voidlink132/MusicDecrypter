package com.musicdecrypter.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.musicdecrypter.BuildConfig;
import com.musicdecrypter.databinding.FragmentSettingsBinding;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.noties.markwon.Markwon;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;

    private final ActivityResultLauncher<String[]> storagePermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                boolean allGranted = true;
                for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                    if (!entry.getValue()) {
                        allGranted = false;
                        break;
                    }
                }
                if (allGranted) {
                    checkManageStoragePermission();
                } else {
                    Toast.makeText(requireContext(), R.string.permission_denied, Toast.LENGTH_SHORT).show();
                }
            }
    );

    private final ActivityResultLauncher<Intent> manageStorageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        Toast.makeText(requireContext(), R.string.permission_granted, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), R.string.permission_denied, Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.tvVersion.setText("v" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
        binding.btnCheckPermission.setOnClickListener(v -> checkAllPermissions());
        renderUsageMarkdown();
    }

    private void checkAllPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                showManageStorageDialog();
                return;
            }
        }

        List<String> permissions = new ArrayList<>();
        permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            permissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        List<String> notGranted = new ArrayList<>();
        for (String permission : permissions) {
            if (requireContext().checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notGranted.add(permission);
            }
        }

        if (!notGranted.isEmpty()) {
            storagePermissionLauncher.launch(notGranted.toArray(new String[0]));
        } else {
            Toast.makeText(requireContext(), R.string.permission_all_granted, Toast.LENGTH_SHORT).show();
        }
    }

    private void checkManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                showManageStorageDialog();
            }
        }
    }

    private void showManageStorageDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.permission_title)
                .setMessage(R.string.permission_manage_storage_tip)
                .setPositiveButton(R.string.go_to_settings, (dialog, which) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
                        manageStorageLauncher.launch(intent);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void renderUsageMarkdown() {
        Markwon markwon = Markwon.create(requireContext());
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(requireContext().getAssets().open("usage.md")));
            StringBuilder markdown = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                markdown.append(line).append("\n");
            }
            reader.close();
            markwon.setMarkdown(binding.tvUsage, markdown.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
