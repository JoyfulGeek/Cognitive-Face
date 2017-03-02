package com.mrpei.face.adapter;

import android.content.Context;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

import com.mrpei.face.fragment.MyFragment;

/**
 * Created by Mr. Pei on 2017/2/24.
 */
public class MyFragmentPagerAdapter extends FragmentPagerAdapter {
    public final int COUNT = 3;
    private String[] titles = new String[]{"聊天", "好友", "设置"};
    private Context context;

    public MyFragmentPagerAdapter(FragmentManager fm, Context context) {
        super(fm);
        this.context = context;
    }

    @Override
    public Fragment getItem(int position) {
        return MyFragment.newInstance(position + 1);
    }

    @Override
    public int getCount() {
        return COUNT;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return titles[position];
    }
}
