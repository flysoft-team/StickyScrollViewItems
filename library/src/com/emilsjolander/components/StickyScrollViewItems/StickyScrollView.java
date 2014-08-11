package com.emilsjolander.components.StickyScrollViewItems;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AdapterView;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * @author Emil Sj�lander - sjolander.emil@gmail.com
 */
public class StickyScrollView extends ScrollViewEx implements StickyInnerScrollableListener {

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

	private MotionEvent lastMotionEvent;
	private MotionEvent needToHandleEvent;
	private VelocityTracker velocityTracker;

	private float startY;
	private float startX;
	private float startYRelative;
	private float startXRelative;


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
		interceptedEvents = new LinkedList<>();
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
		if (savedState != null) {
			if (savedState.scrollToBottom) {
				scrollTo(getScrollX(), getScrollRange());
			}
			savedState = null;
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

		if (touchesState == TouchesState.FLING_SCROLLABLE || touchesState == TouchesState.FLING_THIS) {
			toUndefined();
		}
		if (currentlyStickingView != null) {
			final int action = ev.getActionMasked();
			if (action == MotionEvent.ACTION_DOWN) {
				updateStickyOnScreenLocationRect();
				if (locationRect.contains((int) ev.getRawX(), (int) ev.getRawY())) {
					toSticky();
					return false;
				}
			} else if ((action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) &&
					touchesState == TouchesState.TO_STICKY) {
				toUndefined();
				return false;
			} else if (touchesState == TouchesState.TO_STICKY) {
				return false;
			}
		}
		catchMotionEvent(ev);
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
						StickyInnerScrollableView scrollableView = canScroll(this, false, 1, (int) startXRelative,
								(int) startYRelative);
						if (scrollableView != null) {
							toTranslateToScrollable(scrollableView);
							return true;
						}

					} else if (deltaY < 0 && deltaY < -touchSlop) {
						StickyInnerScrollableView scrollableView = canScroll(this, false, -1, (int) startXRelative,
								(int) startYRelative);
						if (scrollableView != null) {
							toTranslateToScrollable(scrollableView);
							return true;
						}
					}
					break;
				}
			}
		}
		final int action = ev.getActionMasked();
		if ((action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP)) {
			toUndefined();
		}
		return super.onInterceptTouchEvent(ev);
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		catchMotionEvent(ev);

		boolean handled = dispatchTouch(ev);

		final int action = ev.getActionMasked();
		if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
			toUndefined();
		}

		if (handled) {
			return true;
		} else {
			return super.onTouchEvent(ev);
		}

	}

	private void catchMotionEvent(MotionEvent event) {
//		if (!(forwardTouchesToScrollable || forwardTouchesFromScrollable)) {
		if (lastMotionEvent != null) {
			lastMotionEvent.recycle();
		}
		lastMotionEvent = MotionEvent.obtain(event);
//		}
	}

	private boolean dispatchTouch(MotionEvent event) {
		boolean handled = false;
		switch (touchesState) {
			case TRANSLATE_TO_SCROLLABLE: {
				if (interceptedEvents.size() > 0) {
					for (MotionEvent cEvent : interceptedEvents) {
						innerScrollableView.getListView().onTouchEvent(cEvent);
					}
					clearEvents(interceptedEvents);
				}
				handled = innerScrollableView.getListView().onTouchEvent(getRelativeEvent(innerScrollableView,
						event));
				break;
			}
			case REDIRECT_TO_SCROLLABLE: {
				if (needToHandleEvent != null) {
//					innerScrollableView.getListView().onTouchEvent(
//							needToHandleEvent);
					innerScrollableView.startScrollByMotionEvents(velocityTracker,
							needToHandleEvent, event);
					needToHandleEvent.recycle();
					needToHandleEvent = null;
					velocityTracker = null;
					handled = true;
					break;
				}
				handled = innerScrollableView.getListView().onTouchEvent(event);
				break;
			}
			case REDIRECT_FROM_SCROLLABLE: {
				if (needToHandleEvent != null) {
//					super.onTouchEvent(needToHandleEvent);
					startScrollByMotionEvents(velocityTracker, needToHandleEvent, event);
					needToHandleEvent.recycle();
					needToHandleEvent = null;
					velocityTracker = null;
					handled = true;
					break;
				}
				handled = super.onTouchEvent(event);
				break;
			}
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
		if (!isBeingDragged()) {
			doTheFlyingThing(t, oldt);
		} else {
			if (t > oldt) {
				if (!canScrollVertically(1)) {
					StickyInnerScrollableView scrollableView = canScroll(this, false, 1, getWidth() / 2, getHeight() / 2);
					if (scrollableView != null) {
						toRedirectToScrollable(scrollableView);

//						setOverScrollMode(View.OVER_SCROLL_NEVER);
					}
				}
			}
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


	private void doTheFlyingThing(int top, int oldTop) {
		if (top > oldTop && touchesState != TouchesState.FLING_SCROLLABLE) {
			if (!canScrollVertically(1)) {
				StickyInnerScrollableView scrollableView = canScroll(this, false, 1, getWidth() / 2, getHeight() / 2);
				if (scrollableView != null) {
					if (scrollableView instanceof AdapterView) {
						stopFling();
						toFlingScrollable(scrollableView, getCurrentFlingVelocity());
					}
				}
			}
		}
	}


	@Override
	public void onScrollableFling(View v, int position, int oldPosition, int t, int oldT, float velocity) {
		if (position <= oldPosition && position == 0 && t > oldT && t == 0 && touchesState != TouchesState
				.FLING_THIS) {
			if (!canScrollVertically(1)) {
				if (!v.canScrollVertically(-1)) {
					toFlingThis(velocity);
				}
			}
		}
	}

	@Override
	public void onScrollableScroll(View v, int position, int oldPosition, int t, int oldT) {
		if (position <= oldPosition && position == 0 && t > oldT && t == 0) {
			if (!canScrollVertically(1)) {
				if (!v.canScrollVertically(-1)) {
					toRedirectFromScrollable((StickyInnerScrollableView) v);
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

	public enum TouchesState {
		UNDEFINED, TO_STICKY, REDIRECT_TO_SCROLLABLE, REDIRECT_FROM_SCROLLABLE,
		TRANSLATE_TO_SCROLLABLE, FLING_SCROLLABLE, FLING_THIS
	}

	private TouchesState touchesState = TouchesState.UNDEFINED;

	private void changeState(TouchesState nState, StickyInnerScrollableView scrollableView) {
		if (innerScrollableView != null) {
			innerScrollableView.setStickyInnerScrollableListener(null);
		}
		switch (touchesState) {
			case FLING_SCROLLABLE: {
				fromFlingScrollable();
				break;
			}
			case FLING_THIS: {
				fromFlingThis();
				break;
			}
			case TRANSLATE_TO_SCROLLABLE: {
				fromTranslateToScrollable();
				break;
			}
		}
		if (needToHandleEvent != null) {
			needToHandleEvent.recycle();
			needToHandleEvent = null;
		}
		innerScrollableView = scrollableView;
		if (innerScrollableView != null) {
			innerScrollableView.setStickyInnerScrollableListener(this);
		}
		touchesState = nState;
	}

	private void toUndefined() {
		changeState(TouchesState.UNDEFINED, innerScrollableView);
	}

	private void toSticky() {
		changeState(TouchesState.TO_STICKY, null);
	}

	private void fromFlingScrollable() {
		innerScrollableView.stopFling();
	}

	private void fromFlingThis() {
		stopFling();
	}

	private void fromTranslateToScrollable() {
		clearEvents(interceptedEvents);
	}

	private void toFlingThis(float velocity) {
		changeState(TouchesState.FLING_THIS, null);
//		int distance = scrollerHelper.getSplineFlingDistance((int)
//				-velocity);
//		int duration = scrollerHelper.getSplineFlingDuration
//				((int) velocity)*4/3;
		fling((int) -velocity);
	}

	private void toFlingScrollable(StickyInnerScrollableView scrollableView, float velocity) {
		changeState(TouchesState.FLING_SCROLLABLE, scrollableView);
//		final AbsListView adapterView = (AbsListView) scrollableView;
//		float currentVelocity = getCurrentFlingVelocity();
//		adapterView.smoothScrollBy(scrollerHelper.getSplineFlingDistance((int)
//				currentVelocity)
//				, scrollerHelper.getSplineFlingDuration((int) currentVelocity)*4/3);
		scrollableView.startFling((int) -velocity);
	}

	private void toTranslateToScrollable(StickyInnerScrollableView scrollableView) {
		changeState(TouchesState.TRANSLATE_TO_SCROLLABLE, scrollableView);
	}

	private void toRedirectToScrollable(StickyInnerScrollableView scrollableView) {
		changeState(TouchesState.REDIRECT_TO_SCROLLABLE, scrollableView);
		velocityTracker = snatchVelocityTracker();
		needToHandleEvent = MotionEvent.obtain(lastMotionEvent);
	}

	private void toRedirectFromScrollable(StickyInnerScrollableView scrollableView) {
		changeState(TouchesState.REDIRECT_FROM_SCROLLABLE, scrollableView);
		velocityTracker = scrollableView.getVelocityTracker();
		MotionEvent nEvent = MotionEvent.obtain(lastMotionEvent.getDownTime(), lastMotionEvent.getEventTime(),
				MotionEvent.ACTION_CANCEL, lastMotionEvent.getX(), lastMotionEvent.getY(),
				lastMotionEvent.getMetaState());
		scrollableView.getListView().onTouchEvent(nEvent);
		nEvent.recycle();
		needToHandleEvent = MotionEvent.obtain(lastMotionEvent);
	}

	private SavedState savedState;

	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		SavedState ss = (SavedState) state;
		super.onRestoreInstanceState(ss.getSuperState());
		savedState = ss;
	}

	@Override
	protected Parcelable onSaveInstanceState() {
		Parcelable superState = super.onSaveInstanceState();
		SavedState ss = new SavedState(superState);
		ss.scrollToBottom = !canScrollVertically(1);
		return ss;
	}

	static class SavedState extends BaseSavedState {
		public boolean scrollToBottom;

		SavedState(Parcelable superState) {
			super(superState);
		}

		public SavedState(Parcel source) {
			super(source);
			scrollToBottom = source.readInt() == 1;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			super.writeToParcel(dest, flags);
			dest.writeInt(scrollToBottom ? 1 : 0);
		}

		public static final Parcelable.Creator<SavedState> CREATOR
				= new Parcelable.Creator<SavedState>() {
			public SavedState createFromParcel(Parcel in) {
				return new SavedState(in);
			}

			public SavedState[] newArray(int size) {
				return new SavedState[size];
			}
		};
	}


}
