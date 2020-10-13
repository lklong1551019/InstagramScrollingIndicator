package com.example.instagramscrollingindicator;

public abstract class AbstractViewPagerAttacher<T> implements ScrollingPagerIndicator.PagerAttacher<T> {

    public void updateIndicatorOnPagerScrolled(ScrollingPagerIndicator indicator, int position) {
        indicator.onPageSettled(position);
    }
}
