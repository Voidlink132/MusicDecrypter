// SearchFragment.java 中修改解密相关回调
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
                break;
            default:
                llProgressArea.setVisibility(View.VISIBLE);
                tvDecryptStep.setText(message);
                tvProgressPercent.setVisibility(View.GONE);
                decryptProgressBar.setIndeterminate(false);
            }
        });
    }
}

// 解密回调适配
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
