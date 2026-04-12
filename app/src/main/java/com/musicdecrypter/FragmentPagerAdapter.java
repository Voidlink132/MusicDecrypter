package com.musicdecrypter.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.musicdecrypter.ui.DecryptFragment;
import com.musicdecrypter.ui.SearchFragment;
import com.musicdecrypter.ui.SettingsFragment;

public class ViewPagerAdapter extends FragmentStateAdapter {

    public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return new DecryptFragment();
            case 1:
                return new SearchFragment();
            case 2:
                return new SettingsFragment();
            default:
                return new DecryptFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}
