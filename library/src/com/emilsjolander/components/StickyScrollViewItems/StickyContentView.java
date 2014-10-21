package com.emilsjolander.components.StickyScrollViewItems;

import android.view.MotionEvent;
import android.view.VelocityTracker;

/**
 * Created by Shad on 28.07.14.
 */
public interface StickyContentView {

	public void setStickyMainContentScrollListener(StickyMainContentScrollListener stickyMainContentScrollListener);

	public void getLocationOnScreen(int[] location);

	public void scrollToTop();

	public void startScrollByEvents(VelocityTracker velocityTracker, MotionEvent prevEvent,
	                                MotionEvent event);

	public void startFling(int velocity);

	public void stopFling();

	public void stopScroll();

	public boolean onTranslatedTouchEvent(MotionEvent event);

	public VelocityTracker getVelocityTracker();

}
