package com.emilsjolander.components.StickyScrollViewItems;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;

/**
 * Created by Shad on 20.10.14.
 */
public class ContentScrollView extends ScrollViewEx implements StickyContentView {

	public ContentScrollView(Context context) {
		super(context);
	}

	public ContentScrollView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public ContentScrollView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	@Override
	protected void onScrollStateChanged(boolean isDrag) {

	}

	@Override
	protected void onScrollChanged(int l, int t, int oldl, int oldt) {
		super.onScrollChanged(l, t, oldl, oldt);
		if (stickyMainContentScrollListener != null) {
			if (mIsBeingDragged) {
				stickyMainContentScrollListener.onScrollableScroll(this, t, oldt, -1, -1);
			} else {
				stickyMainContentScrollListener.onScrollableFling(this, t, oldt,
						-1, -1, getCurrentFlingVelocity());
			}
		}
	}


	private StickyMainContentScrollListener stickyMainContentScrollListener;

	@Override
	public void setStickyMainContentScrollListener(StickyMainContentScrollListener stickyMainContentScrollListener) {
		this.stickyMainContentScrollListener = stickyMainContentScrollListener;
	}

	@Override
	public void startScrollByEvents(VelocityTracker velocityTracker, MotionEvent prevEvent,
	                                MotionEvent event, int pointerId) {
		startScrollByMotionEvents(velocityTracker, prevEvent, event, pointerId);
	}

	@Override
	public void scrollToTop() {
		scrollTo(0, 0);
	}

	@Override
	public void startFling(int velocity) {
		stopAndFly(-velocity);
	}

	@Override
	public void stopScroll() {
		endDrag();
	}

	@Override
	public boolean onTranslatedTouchEvent(MotionEvent event) {
		return onTouchEvent(event);
	}

}
