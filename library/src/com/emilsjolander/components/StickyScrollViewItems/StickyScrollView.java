package com.emilsjolander.components.StickyScrollViewItems;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.OverScroller;
import android.widget.ScrollView;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * @author Emil Sj�lander - sjolander.emil@gmail.com
 */
public class StickyScrollView extends ScrollView implements StickyInnerScrollableListener {

	private static final String TAG = StickyScrollView.class.getSimpleName();

	/**
	 * Default height of the shadow peeking out below the stuck view.
	 */
	private static final int DEFAULT_SHADOW_HEIGHT = 10; // dp;

	private View stickyView;
	private View currentlyStickingView;
	private StickyInnerScrollableView innerScrollableView;

	private Queue<MotionEvent> interceptedEvents;

	private float stickyViewTopOffset;
	private int stickyViewLeftOffset;
	private boolean clippingToPadding;
	private boolean clipToPaddingHasBeenSet;

	private int mShadowHeight;
	private Drawable mShadowDrawable;

	private int stickyViewId;

	private int touchSlop;

	private Field isBeenDragged;
	private OverScroller scroller;
	private ScrollerHelper scrollerHelper;

	private MotionEvent lastMotionEvent;

	private float startY;
	private float startX;
	private float startYRelative;
	private float startXRelative;
	private boolean forwardTouchesToStickyView;

	private boolean forwardTouchesToScrollable;
	private boolean redirectTouchesToScrollable;

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

		a.recycle();

	}

	public void setStickyViewId(int stickyViewId) {
		this.stickyViewId = stickyViewId;
		findStickyViews();
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
		scrollerHelper = new ScrollerHelper(getContext());
		interceptedEvents = new LinkedList<>();
		final ViewConfiguration configuration = ViewConfiguration.get(getContext());
		touchSlop = configuration.getScaledTouchSlop();

		try {

			Class clazz = ScrollView.class;
			isBeenDragged = clazz.getDeclaredField("mIsBeingDragged");
			isBeenDragged.setAccessible(true);

			Field field = clazz.getDeclaredField("mScroller");
			field.setAccessible(true);
			scroller = (OverScroller) field.get(this);
		} catch (NoSuchFieldException | IllegalAccessException e) {
		}
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

	private MotionEvent getRelativeEvent(StickyInnerScrollableView v, MotionEvent original) {
		MotionEvent relative = MotionEvent.obtain(original);
		if (original.getX() == original.getRawX() && original.getY() == original.getRawY()) {
			v.getLocationOnScreen(location);
			relative.offsetLocation(-location[0], -location[1]);
		}
		return relative;
	}


	private void clearEvents(Queue<MotionEvent> events) {
		for (MotionEvent event : events) {
			event.recycle();
		}
		events.clear();
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		flingStarted = false;
		if (innerScrollableView != null) {
			innerScrollableView.setStickyInnerScrollableListener(null);
			innerScrollableView = null;
		}
		setOverScrollMode(View.OVER_SCROLL_ALWAYS);
		if (currentlyStickingView != null) {
			final int action = ev.getActionMasked();
			if (action == MotionEvent.ACTION_DOWN) {
				updateStickyOnScreenLocationRect();
				if (locationRect.contains((int) ev.getRawX(), (int) ev.getRawY())) {
					forwardTouchesToStickyView = true;
					return false;
				}
			} else if ((action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) && forwardTouchesToStickyView) {
				forwardTouchesToStickyView = false;
				return false;
			} else if (forwardTouchesToStickyView) {
				return false;
			}
		}

		if (!canScrollVertically(1)) {
			final int action = ev.getActionMasked();
			switch (action) {
				case MotionEvent.ACTION_DOWN: {
					clearEvents(interceptedEvents);
					startY = ev.getRawY();
					startX = ev.getRawX();
					startYRelative = ev.getY();
					startXRelative = ev.getX();
					interceptedEvents.offer(MotionEvent.obtain(ev));
					break;
				}
				case MotionEvent.ACTION_MOVE: {
					float y = ev.getRawY();
					float deltaY = startY - y;
					if (deltaY > 0 && deltaY > touchSlop) {
						innerScrollableView = canScroll(this, false, 1, (int) startXRelative,
								(int) startYRelative);
						Log.d(TAG, "touch detected");
						if (innerScrollableView != null) {
							redirectTouchesToScrollable = true;
							Log.d(TAG, "touch accepted");
							return true;
						}

					} else if (deltaY < 0 && deltaY < -touchSlop) {
						innerScrollableView = canScroll(this, false, -1, (int) startXRelative,
								(int) startYRelative);
						if (innerScrollableView != null) {
							redirectTouchesToScrollable = true;
							innerScrollableView.setStickyInnerScrollableListener(this);
							return true;
						}
					}
					break;
				}
				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_CANCEL: {
					redirectTouchesToScrollable = false;
					clearEvents(interceptedEvents);
					return false;
				}
			}
		} else {
			clearEvents(interceptedEvents);
		}
		if (!forwardTouchesToScrollable) {
			lastMotionEvent = MotionEvent.obtain(ev);
		}

		return super.onInterceptTouchEvent(ev);
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {

		if (redirectTouchesToScrollable) {
			if (interceptedEvents.size() > 0) {
				for (MotionEvent event : interceptedEvents) {
					innerScrollableView.getListView().onTouchEvent(event);
				}
				clearEvents(interceptedEvents);
			}

			return dispatchTouch(ev);
		} else if (forwardTouchesToScrollable) {
			if (lastMotionEvent != null) {
				innerScrollableView.getListView().onTouchEvent(
						lastMotionEvent);
				lastMotionEvent.recycle();
				lastMotionEvent = null;
			}

			return dispatchTouch(ev);
		}

		final int action = ev.getActionMasked();
		if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
			if (!forwardTouchesToScrollable) {
				lastMotionEvent.recycle();
				lastMotionEvent = null;
				forwardTouchesToScrollable = false;
			}

		} else {
			if (!forwardTouchesToScrollable) {
				lastMotionEvent = MotionEvent.obtain(ev);
			}
		}


		return super.onTouchEvent(ev);
	}

	private boolean dispatchTouch(MotionEvent event) {
		if (innerScrollableView == null) {
			return false;
		}
		boolean handled = false;
		if (redirectTouchesToScrollable) {
			handled = innerScrollableView.getListView().onTouchEvent(getRelativeEvent(innerScrollableView,
					event));
		} else if (forwardTouchesToScrollable) {
			handled = innerScrollableView.getListView().onTouchEvent(event);
		}
		final int action = event.getActionMasked();
		if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
			forwardTouchesToScrollable = false;
			redirectTouchesToScrollable= false;
		}

		return handled;
	}

	protected StickyInnerScrollableView canScroll(View v, boolean checkV, int direction, int x, int y) {
		if (v instanceof ViewGroup) {
			final ViewGroup group = (ViewGroup) v;
			final int scrollX = v.getScrollX();
			final int scrollY = v.getScrollY();
			final int count = group.getChildCount();
			// Count backwards - let topmost views consume scroll distance first.
			for (int i = count - 1; i >= 0; i--) {
				// This will not work for transformed views in Honeycomb+
				final View child = group.getChildAt(i);
				if (x + scrollX >= child.getLeft() && x + scrollX < child.getRight() &&
						y + scrollY >= child.getTop() && y + scrollY < child.getBottom()
						) {
					StickyInnerScrollableView view = canScroll(child, true, direction, x + scrollX - child.getLeft(),
							y + scrollY - child.getTop());
					if (view != null) {
						return view;
					}
				}
			}
		}
		boolean canScroll = v instanceof StickyInnerScrollableView && checkV && v.canScrollVertically(direction);
//		boolean canScroll = false;
//		if (v instanceof  StickyInnerScrollableView)
//		{
//			canScroll = checkV && v.canScrollVertically(direction);
//		}
		return canScroll ? (StickyInnerScrollableView) v : null;
	}


	@Override
	protected void onScrollChanged(int l, int t, int oldl, int oldt) {
		super.onScrollChanged(l, t, oldl, oldt);
		doTheStickyThing();
		if (!isBeenDragged()) {
			doTheFlyingThing(t, oldt);
		} else {
			if (t > oldt) {
				if (!canScrollVertically(1)) {
					StickyInnerScrollableView scrollableView = canScroll(this, false, 1, getWidth() / 2, getHeight() / 2);
					if (scrollableView != null) {
						innerScrollableView = scrollableView;
						forwardTouchesToScrollable = true;
						MotionEvent nEvent = MotionEvent.obtain(lastMotionEvent.getDownTime(), lastMotionEvent.getEventTime(),
								MotionEvent.ACTION_DOWN, lastMotionEvent.getX(), lastMotionEvent.getY(),
								lastMotionEvent.getMetaState());
						lastMotionEvent.recycle();
						lastMotionEvent = nEvent;
						setOverScrollMode(View.OVER_SCROLL_NEVER);
					}
				}
			}
		}

	}

	private boolean isBeenDragged() {
		try {
			return isBeenDragged.getBoolean(this);
		} catch (IllegalAccessException e) {
			return false;
		}
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

	private boolean flingStarted = false;

	private void doTheFlyingThing(int top, int oldTop) {
		if (top > oldTop && !flingStarted) {
			if (!canScrollVertically(1)) {
				StickyInnerScrollableView scrollableView = canScroll(this, false, 1, getWidth() / 2, getHeight() / 2);
				if (scrollableView != null) {
					if (scrollableView instanceof AdapterView) {
						final AbsListView adapterView = (AbsListView) scrollableView;
						float currentVelocity = scroller.getCurrVelocity();
						adapterView.smoothScrollBy(scrollerHelper.getSplineFlingDistance((int)
								currentVelocity)
								, scrollerHelper.getSplineFlingDuration((int) currentVelocity));
						flingStarted = true;
						setOverScrollMode(View.OVER_SCROLL_NEVER);
					}
				}
			}
		}
	}


	@Override
	public void onFling(View v, int t, int oldT, float velocity) {
		if (t <= oldT && t == 0 && !flingStarted) {
			if (!canScrollVertically(1)) {
				if (!v.canScrollVertically(-1)) {
					int distance = scrollerHelper.getSplineFlingDistance((int)
							-velocity);
					final int height = getHeight() - getPaddingBottom() - getPaddingTop();
					final int bottom = getChildAt(0).getHeight();
					final int maxY = Math.max(0, bottom - height);
					final int scrollY = getScrollY();
					int dy = Math.max(0, Math.min(scrollY + distance, maxY)) - scrollY;
					int duration = scrollerHelper.getSplineFlingDuration
							((int) velocity);
					scroller.startScroll(getScrollX(), scrollY, 0, dy, duration);
					postInvalidateOnAnimation();
					flingStarted = true;
				}
			}
		}
	}

	private void startStickingView(View viewThatShouldStick) {
		currentlyStickingView = viewThatShouldStick;
		currentlyStickingView.bringToFront();
		requestLayout();
		invalidate();
	}

	private void stopStickingCurrentlyStickingView() {
		currentlyStickingView.setTranslationY(0);
		currentlyStickingView = null;
	}

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
	}

	private void findInnerScrollables(View v, List<StickyInnerScrollableView> scrollables, boolean checkThis) {
		if (checkThis && v instanceof StickyInnerScrollableView) {
			scrollables.add((StickyInnerScrollableView) v);
		} else if (v instanceof ViewGroup) {
			ViewGroup viewGroup = (ViewGroup) v;
			int childrenCount = viewGroup.getChildCount();
			for (int i = 0; i < childrenCount; ++i) {
				View child = viewGroup.getChildAt(i);
				findInnerScrollables(child, scrollables, true);
			}
		}
	}

	public void syncInnerScrollables() {
		if (canScrollVertically(1)) {
			List<StickyInnerScrollableView> scrollables = new ArrayList<>();
			findInnerScrollables(this, scrollables, false);
			for (StickyInnerScrollableView scrollableView : scrollables) {
				scrollableView.scrollToTop();
			}
		}
	}
}
