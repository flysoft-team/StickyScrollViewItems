package com.emilsjolander.components.StickyScrollViewItems;

import android.graphics.Point;
import android.view.MotionEvent;
import android.widget.AbsListView;

/**
 * Created by Shad on 28.07.14.
 */
public interface StickyInnerScrollableView {

	public void setStickyInnerScrollableListener(StickyInnerScrollableListener stickyInnerScrollableListener);

	public AbsListView getListView();

	public void getLocationOnScreen(int[] location);

	public void scrollToTop();

	public void startScrollByMotionEvents(MotionEvent prevEvent, MotionEvent event);

	public void startFling(int velocity);

	public void stopFling();

}
