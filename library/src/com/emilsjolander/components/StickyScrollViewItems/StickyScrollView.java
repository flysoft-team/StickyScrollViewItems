package com.emilsjolander.components.StickyScrollViewItems;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ScrollView;

import java.util.ArrayList;

/**
 * @author Emil Sj�lander - sjolander.emil@gmail.com
 */
public class StickyScrollView extends ScrollView {

	/**
	 * Flag for views that should stick and have non-constant drawing. e.g. Buttons, ProgressBars etc
	 */
	public static final String FLAG_NONCONSTANT = "-nonconstant";

	/**
	 * Flag for views that have aren't fully opaque
	 */
	public static final String FLAG_HASTRANSPARANCY = "-hastransparancy";

	/**
	 * Default height of the shadow peeking out below the stuck view.
	 */
	private static final int DEFAULT_SHADOW_HEIGHT = 10; // dp;

	private View stickyView;
	private View currentlyStickingView;
	private View innerScrollableView;

	private ArrayList<MotionEvent> interceptedEvents;

	private float stickyViewTopOffset;
	private int stickyViewLeftOffset;
	private boolean clippingToPadding;
	private boolean clipToPaddingHasBeenSet;

	private int mShadowHeight;
	private Drawable mShadowDrawable;

	private int stickyViewId;
	private int scrollableViewId;

	private int touchSlop;

	private final Runnable invalidateRunnable = new Runnable() {

		@Override
		public void run() {
			if (currentlyStickingView != null) {
				int l = getLeftForViewRelativeOnlyChild(currentlyStickingView);
				int t = getBottomForViewRelativeOnlyChild(currentlyStickingView);
				int r = getRightForViewRelativeOnlyChild(currentlyStickingView);
				int b = (int) (getScrollY() + (currentlyStickingView.getHeight() + stickyViewTopOffset));
				invalidate(l, t, r, b);
			}
			postOnAnimationDelayed(this, 16);
		}
	};

	public StickyScrollView(Context context) {
		this(context, null);
	}

	public StickyScrollView(Context context, AttributeSet attrs) {
		this(context, attrs, android.R.attr.scrollViewStyle);
	}

	public StickyScrollView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		setup();


		TypedArray a = context.obtainStyledAttributes(attrs,
				R.styleable.StickyScrollView, defStyle, 0);

		final float density = context.getResources().getDisplayMetrics().density;
		int defaultShadowHeightInPix = (int) (DEFAULT_SHADOW_HEIGHT * density + 0.5f);

		mShadowHeight = a.getDimensionPixelSize(
				R.styleable.StickyScrollView_stuckShadowHeight,
				defaultShadowHeightInPix);

		int shadowDrawableRes = a.getResourceId(
				R.styleable.StickyScrollView_stuckShadowDrawable, -1);

		if (shadowDrawableRes != -1) {
			mShadowDrawable = context.getResources().getDrawable(
					shadowDrawableRes);
		}

		stickyViewId = a.getResourceId(R.styleable.StickyScrollView_stickyView, 0);
		scrollableViewId = a.getResourceId(R.styleable.StickyScrollView_scrollableView, 0);

		a.recycle();

	}

	/**
	 * Sets the height of the shadow drawable in pixels.
	 *
	 * @param height
	 */
	public void setShadowHeight(int height) {
		mShadowHeight = height;
	}


	public void setup() {
		interceptedEvents = new ArrayList<MotionEvent>();
		final ViewConfiguration configuration = ViewConfiguration.get(getContext());
		touchSlop = configuration.getScaledTouchSlop();
	}

	private int getLeftForViewRelativeOnlyChild(View v) {
		int left = v.getLeft();
		while (v.getParent() != getChildAt(0)) {
			v = (View) v.getParent();
			left += v.getLeft();
		}
		return left;
	}

	private int getTopForViewRelativeOnlyChild(View v) {
		int top = v.getTop();
		while (v.getParent() != getChildAt(0)) {
			v = (View) v.getParent();
			top += v.getTop();
		}
		return top;
	}

	private int getRightForViewRelativeOnlyChild(View v) {
		int right = v.getRight();
		while (v.getParent() != getChildAt(0)) {
			v = (View) v.getParent();
			right += v.getRight();
		}
		return right;
	}

	private int getBottomForViewRelativeOnlyChild(View v) {
		int bottom = v.getBottom();
		while (v.getParent() != getChildAt(0)) {
			v = (View) v.getParent();
			bottom += v.getBottom();
		}
		return bottom;
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		super.onLayout(changed, l, t, r, b);
		if (!clipToPaddingHasBeenSet) {
			clippingToPadding = true;
		}
//		notifyHierarchyChanged();
	}

	@Override
	public void setClipToPadding(boolean clipToPadding) {
		super.setClipToPadding(clipToPadding);
		clippingToPadding = clipToPadding;
		clipToPaddingHasBeenSet = true;
	}

	@Override
	public void addView(View child) {
		super.addView(child);
		findStickyViews();
	}

	@Override
	public void addView(View child, int index) {
		super.addView(child, index);
		findStickyViews();
	}

	@Override
	public void addView(View child, int index, android.view.ViewGroup.LayoutParams params) {
		super.addView(child, index, params);
		findStickyViews();
	}

	@Override
	public void addView(View child, int width, int height) {
		super.addView(child, width, height);
		findStickyViews();
	}

	@Override
	public void addView(View child, android.view.ViewGroup.LayoutParams params) {
		super.addView(child, params);
		findStickyViews();
	}

	private int[] location = new int[2];

	private Rect locationRect = new Rect();

	private void updateStickyOnScreenLocationRect() {
		currentlyStickingView.getLocationOnScreen(location);
		locationRect.set(location[0], location[1], location[0] + currentlyStickingView.getWidth
				(), location[1] + currentlyStickingView.getHeight());
	}

	private MotionEvent getRelativeEvent(View v, MotionEvent original) {
		MotionEvent relative = MotionEvent.obtain(original);
		if (original.getX() == original.getRawX() && original.getY() == original.getRawY()) {
			v.getLocationOnScreen(location);
			relative.offsetLocation(-location[0], -location[1]);
		}
		return relative;
	}

	private float startY;

	private boolean touchesToScrollable;
	private boolean isRedirectTouchesToStickyView;

	private void clearEvents() {
		for (MotionEvent event : interceptedEvents) {
			event.recycle();
		}
		interceptedEvents.clear();
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {

		if (currentlyStickingView != null) {
			final int action = ev.getActionMasked();
			if (action == MotionEvent.ACTION_DOWN) {
				updateStickyOnScreenLocationRect();
				if (locationRect.contains((int) ev.getRawX(), (int) ev.getRawY())) {
					isRedirectTouchesToStickyView = true;
					return false;
				}
			} else if ((action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) && isRedirectTouchesToStickyView) {
				isRedirectTouchesToStickyView = false;
				return false;
			} else if (isRedirectTouchesToStickyView) {
				return false;
			}
		}

		if (!canScrollVertically(1)) {
			final int action = ev.getActionMasked();
			switch (action) {
				case MotionEvent.ACTION_DOWN: {
					clearEvents();
					startY = ev.getRawY();
					interceptedEvents.add(getRelativeEvent(innerScrollableView, ev));
					break;
				}
				case MotionEvent.ACTION_MOVE: {
					float y = ev.getRawY();
					float deltaY = startY - y;
					if (deltaY > 0 && deltaY > touchSlop && innerScrollableView.canScrollVertically(1)) {
						touchesToScrollable = true;

						return true;
					} else if (deltaY < 0 && deltaY < -touchSlop && innerScrollableView.canScrollVertically(0)) {
						touchesToScrollable = true;

						return true;
					}
					break;
				}
				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_CANCEL: {
					touchesToScrollable = false;
					clearEvents();
					return false;
				}
			}
		} else {
			clearEvents();
		}

		return super.onInterceptTouchEvent(ev);
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		if (touchesToScrollable) {
			if (interceptedEvents.size() > 0) {
				for (MotionEvent event : interceptedEvents) {
					innerScrollableView.onTouchEvent(event);
				}
				clearEvents();
			}
			final int action = ev.getActionMasked();
			if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
				touchesToScrollable = false;
			}
			return innerScrollableView.onTouchEvent(getRelativeEvent(innerScrollableView, ev));
		}

		return super.onTouchEvent(ev);
	}

	@Override
	protected void onScrollChanged(int l, int t, int oldl, int oldt) {
		super.onScrollChanged(l, t, oldl, oldt);
		doTheStickyThing();
	}

	private void doTheStickyThing() {
		View viewThatShouldStick = null;
		View approachingView = null;
		int viewTop = getTopForViewRelativeOnlyChild(stickyView) - getScrollY() + (clippingToPadding ? 0 :
				getPaddingTop());
		if (viewTop <= 0) {
			if (viewThatShouldStick == null || viewTop > (getTopForViewRelativeOnlyChild(viewThatShouldStick) - getScrollY() + (clippingToPadding ? 0 : getPaddingTop()))) {
				viewThatShouldStick = stickyView;
			}
		} else {
			if (approachingView == null || viewTop < (getTopForViewRelativeOnlyChild(approachingView) - getScrollY() + (clippingToPadding ? 0 : getPaddingTop()))) {
				approachingView = stickyView;
			}
		}
		if (viewThatShouldStick != null) {
			stickyViewTopOffset = approachingView == null ? 0 : Math.min(0, getTopForViewRelativeOnlyChild(approachingView) - getScrollY() + (clippingToPadding ? 0 : getPaddingTop()) - viewThatShouldStick.getHeight());
			if (viewThatShouldStick != currentlyStickingView) {
				if (currentlyStickingView != null) {
					stopStickingCurrentlyStickingView();
				}
				// only compute the left offset when we start sticking.
				stickyViewLeftOffset = getLeftForViewRelativeOnlyChild(viewThatShouldStick);
				startStickingView(viewThatShouldStick);
			}
		} else if (currentlyStickingView != null) {
			stopStickingCurrentlyStickingView();
		}

		if (currentlyStickingView != null) {
			currentlyStickingView.setTranslationY((clippingToPadding ? 0 : getPaddingTop()) -
					getTopForViewRelativeOnlyChild(currentlyStickingView) + getScrollY());
		}
	}

	private void startStickingView(View viewThatShouldStick) {
		currentlyStickingView = viewThatShouldStick;
		currentlyStickingView.bringToFront();
		requestLayout();
		invalidate();
		if (((String) currentlyStickingView.getTag()).contains(FLAG_NONCONSTANT)) {
			postOnAnimation(invalidateRunnable);
		}
	}

	private void stopStickingCurrentlyStickingView() {
		currentlyStickingView.setTranslationY(0);
		currentlyStickingView = null;
		removeCallbacks(invalidateRunnable);
	}

	/**
	 * Notify that the sticky attribute has been added or removed from one or more views in the View hierarchy
	 */
	public void notifyStickyAttributeChanged() {
		notifyHierarchyChanged();
	}

	private void notifyHierarchyChanged() {
		if (currentlyStickingView != null) {
			stopStickingCurrentlyStickingView();
		}
		stickyView = null;
		findStickyViews();
		doTheStickyThing();
		invalidate();
	}

	private void findStickyViews() {
		stickyView = findViewById(stickyViewId);
		innerScrollableView = findViewById(scrollableViewId);
	}

	private void hideView(View v) {
		v.setAlpha(0);
	}

	private void showView(View v) {
		v.setAlpha(1);
	}

}
