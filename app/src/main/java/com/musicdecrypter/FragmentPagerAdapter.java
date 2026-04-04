package com.musicdecrypter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.musicdecrypter.ui.DecryptFragment;
import com.musicdecrypter.ui.SearchFragment;
import com.musicdecrypter.ui.SettingsFragment;

public class FragmentPagerAdapter extends FragmentStateAdapter {

    public FragmentPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0: return new SearchFragment();
            case 1: return new DecryptFragment();
            case 2: return new SettingsFragment();
            default: return new SearchFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}
