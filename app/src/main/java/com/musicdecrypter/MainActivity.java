package com.musicdecrypter;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import android.os.Bundle;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.musicdecrypter.adapter.ViewPagerAdapter;

public class MainActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewPager = findViewById(R.id.view_pager);
        bottomNav = findViewById(R.id.bottom_nav);

        ViewPagerAdapter adapter = new ViewPagerAdapter(this);
        viewPager.setAdapter(adapter);
        viewPager.setUserInputEnabled(false);

        bottomNav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_decrypt) {
                viewPager.setCurrentItem(0);
            } else if (item.getItemId() == R.id.nav_search) {
                viewPager.setCurrentItem(1);
            } else if (item.getItemId() == R.id.nav_settings) {
                viewPager.setCurrentItem(2);
            }
            return true;
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                bottomNav.getMenu().getItem(position).setChecked(true);
            }
        });
    }
}
