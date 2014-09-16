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
import android.view.ViewPropertyAnimator;
import android.widget.AdapterView;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import uk.co.chrisjenx.paralloid.ParallaxViewController;
import uk.co.chrisjenx.paralloid.Parallaxor;
import uk.co.chrisjenx.paralloid.transform.Transformer;

/**
 * @author Emil Sj�lander - sjolander.emil@gmail.com
 */
public class StickyScrollView extends ScrollViewEx implements StickyMainContentScrollListener,
		Parallaxor {

	private static final String TAG = StickyScrollView.class.getSimpleName();

	/**
	 * Default height of the shadow peeking out below the stuck view.
	 */
	private static final int DEFAULT_SHADOW_HEIGHT = 10; // dp;

	private ViewPropertyAnimator currentAnimator;
	private final int animationDuration;
	private View stickyView;
	private boolean isStick;
	private boolean isStickyHidden;
	private StickyMainContentView mainContentView;
	private StickyScrollListener stickyScrollListener;

	private Queue<MotionEvent> interceptedEvents;

	private boolean clippingToPadding;
	private boolean clipToPaddingHasBeenSet;

	private int stickOffsetY;

	private int stickyViewId;

	private int touchSlop;

	private MotionEvent lastMotionEvent;
	private MotionEvent needToHandleEvent;
	private VelocityTracker velocityTracker;

	private float startY;
	private float startX;
	private float startYRelative;
	private float startXRelative;

	ParallaxViewController parallaxViewController;

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

		stickOffsetY = a.getDimensionPixelSize(
				R.styleable.StickyScrollView_stickOffsetY, 0);

		stickyViewId = a.getResourceId(R.styleable.StickyScrollView_stickyView, 0);

		a.recycle();

		animationDuration = context.getResources().getInteger(android.R.integer.config_mediumAnimTime);

	}

	public void setStickyViewId(int stickyViewId) {
		this.stickyViewId = stickyViewId;
		findStickyViews();
	}

	public void setStickyOffsetY(int stickOffsetY) {
		this.stickOffsetY = stickOffsetY;
	}

	public void setup() {
		interceptedEvents = new LinkedList<>();
		final ViewConfiguration configuration = ViewConfiguration.get(getContext());
		touchSlop = configuration.getScaledTouchSlop();

		parallaxViewController = ParallaxViewController.wrap(this);
	}

	public void setStickyScrollListener(StickyScrollListener stickyScrollListener) {
		this.stickyScrollListener = stickyScrollListener;
	}

	public int getAnimationDuration() {
		return animationDuration;
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
		stickyView.getLocationOnScreen(location);
		locationRect.set(location[0], location[1], location[0] + stickyView.getWidth
				(), location[1] + stickyView.getHeight());
	}

	private MotionEvent getRelativeEvent(StickyMainContentView v, MotionEvent original) {
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
		if (isStick && !isStickyHidden) {
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
						StickyMainContentView scrollableView = canScroll(this, false, 0,
								(int) startXRelative,
								(int) startYRelative);
						if (scrollableView != null) {
							toTranslateToScrollable(scrollableView);
							return true;
						}

					} else if (deltaY < 0 && deltaY < -touchSlop) {
						StickyMainContentView scrollableView = canScroll(this, false, -1, (int) startXRelative,
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
						mainContentView.onTranslatedTouchEvent(cEvent);
					}
					clearEvents(interceptedEvents);
				}
				handled = mainContentView.onTranslatedTouchEvent(getRelativeEvent(mainContentView,
						event));
				break;
			}
			case REDIRECT_TO_SCROLLABLE: {
				if (needToHandleEvent != null) {
//					mainContentView.getListView().onTouchEvent(
//							needToHandleEvent);
					mainContentView.startScrollByMotionEvents(velocityTracker,
							needToHandleEvent, event);
					needToHandleEvent.recycle();
					needToHandleEvent = null;
					velocityTracker = null;
					handled = true;
					break;
				}
				handled = mainContentView.onTranslatedTouchEvent(event);
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

	protected StickyMainContentView canScroll(View v, boolean checkV, int direction, int x, int y) {
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
					StickyMainContentView view = canScroll(child, true, direction, x + scrollX - child.getLeft(),
							y + scrollY - child.getTop());
					if (view != null) {
						return view;
					}
				}
			}
		}
		boolean canScroll = v instanceof StickyMainContentView && checkV && (direction == 0 || v
				.canScrollVertically(direction));
		return canScroll ? (StickyMainContentView) v : null;
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
					StickyMainContentView scrollableView = canScroll(this, false, 1, getWidth() / 2, getHeight() / 2);
					if (scrollableView != null) {
						toRedirectToScrollable(scrollableView);

//						setOverScrollMode(View.OVER_SCROLL_NEVER);
					}
				}
			}
		}
		if (stickyScrollListener != null) {
			stickyScrollListener.onScrollViewScrolled(t, oldt, getScrollRange());
		}

		parallaxViewController.onScrollChanged(this, l, t, oldl, oldt);
	}

	public void showSticky(boolean show) {
		if (isStick && isStickyHidden == show) {
			if (currentAnimator != null) {
				currentAnimator.cancel();
			}
			if (show) {
				currentAnimator = stickyView.animate().translationY(getStickTranslation());

			} else {
				currentAnimator = stickyView.animate().translationYBy(-(clippingToPadding ? 0 : getPaddingTop()) - stickOffsetY - stickyView
						.getHeight())
						.setDuration(getAnimationDuration());
			}
			isStickyHidden = !show;
			if (stickyScrollListener != null) {
				stickyScrollListener.onStickyVisibilityChanged(isStickyHidden);
			}
		}
	}

	private int getStickTranslation() {
		return (clippingToPadding ? 0 : getPaddingTop()) -
				getTopForViewRelativeOnlyChild(stickyView) + getScrollY() + stickOffsetY;
	}

	private void doTheStickyThing() {
		int viewTop = getTopForViewRelativeOnlyChild(stickyView) - getScrollY() + (clippingToPadding ? 0 :
				getPaddingTop());
		if (viewTop <= stickOffsetY) {
			if (!isStick) {
				startStick();
			}
		} else {
			if (isStick) {
				stopStick();
			}
		}

		if (isStick && !isStickyHidden) {
			stickyView.setTranslationY(getStickTranslation());
		}
	}


	private void doTheFlyingThing(int top, int oldTop) {
		if (top > oldTop && touchesState != TouchesState.FLING_SCROLLABLE) {
			if (!canScrollVertically(1)) {
				StickyMainContentView scrollableView = canScroll(this, false, 1, getWidth() / 2, getHeight() / 2);
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
		if (stickyScrollListener != null) {
			stickyScrollListener.onMainContentScrolled(v, position, oldPosition);
		}
	}

	@Override
	public void onScrollableScroll(View v, int position, int oldPosition, int t, int oldT) {
		if (position <= oldPosition && position == 0 && t > oldT && t == 0) {
			if (!canScrollVertically(1)) {
				if (!v.canScrollVertically(-1)) {
					toRedirectFromScrollable((StickyMainContentView) v);
				}
			}
		}

		if (stickyScrollListener != null) {
			stickyScrollListener.onMainContentScrolled(v, position, oldPosition);
		}
	}

	private void stopStickyShowAnimation() {
		isStickyHidden = false;
		if (currentAnimator != null) {
			currentAnimator.cancel();
			currentAnimator = null;
		}
		if (stickyScrollListener != null) {
			stickyScrollListener.onStickyVisibilityChanged(isStickyHidden);
		}
		stickyView.setTranslationY(0);
	}

	private void startStick() {
		isStick = true;
		stopStickyShowAnimation();
		stickyView.bringToFront();
		requestLayout();
		invalidate();
	}

	private void stopStick() {
		stopStickyShowAnimation();
		isStick = false;
	}

	public void notifyStickyAttributeChanged() {
		notifyHierarchyChanged();
	}

	private void notifyHierarchyChanged() {
		if (isStick) {
			stopStick();
		}
		stickyView = null;
		findStickyViews();
		doTheStickyThing();
		invalidate();
	}

	private void findStickyViews() {
		stickyView = findViewById(stickyViewId);
	}

	private void findInnerScrollables(View v, List<StickyMainContentView> scrollables, boolean checkThis) {
		if (checkThis && v instanceof StickyMainContentView) {
			scrollables.add((StickyMainContentView) v);
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
			List<StickyMainContentView> scrollables = new ArrayList<>();
			findInnerScrollables(this, scrollables, false);
			for (StickyMainContentView scrollableView : scrollables) {
				scrollableView.scrollToTop();
			}
		}
	}

	public enum TouchesState {
		UNDEFINED, TO_STICKY, REDIRECT_TO_SCROLLABLE, REDIRECT_FROM_SCROLLABLE,
		TRANSLATE_TO_SCROLLABLE, FLING_SCROLLABLE, FLING_THIS
	}

	private TouchesState touchesState = TouchesState.UNDEFINED;

	private void changeState(TouchesState nState, StickyMainContentView scrollableView) {
		if (mainContentView != null) {
			mainContentView.setStickyMainContentScrollListener(null);
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
		mainContentView = scrollableView;
		if (mainContentView != null) {
			mainContentView.setStickyMainContentScrollListener(this);
		}
		touchesState = nState;
	}

	private void toUndefined() {
		changeState(TouchesState.UNDEFINED, mainContentView);
	}

	private void toSticky() {
		changeState(TouchesState.TO_STICKY, null);
	}

	private void fromFlingScrollable() {
		mainContentView.stopFling();
	}

	private void fromFlingThis() {
		stopFling();
	}

	private void fromTranslateToScrollable() {
		clearEvents(interceptedEvents);
	}

	private void toFlingThis(float velocity) {
		changeState(TouchesState.FLING_THIS, null);
		stopAndFly((int) -velocity);

	}

	private void toFlingScrollable(StickyMainContentView scrollableView, float velocity) {
		changeState(TouchesState.FLING_SCROLLABLE, scrollableView);
		scrollableView.startFling((int) -velocity);
	}

	private void toTranslateToScrollable(StickyMainContentView scrollableView) {
		changeState(TouchesState.TRANSLATE_TO_SCROLLABLE, scrollableView);
	}

	private void toRedirectToScrollable(StickyMainContentView scrollableView) {
		changeState(TouchesState.REDIRECT_TO_SCROLLABLE, scrollableView);
		velocityTracker = snatchVelocityTracker();
		needToHandleEvent = MotionEvent.obtain(lastMotionEvent);
		endDrag();
	}

	private void toRedirectFromScrollable(StickyMainContentView scrollableView) {
		changeState(TouchesState.REDIRECT_FROM_SCROLLABLE, scrollableView);
		velocityTracker = scrollableView.getVelocityTracker();
		needToHandleEvent = MotionEvent.obtain(lastMotionEvent);
		scrollableView.stopScroll();
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

	public static class SavedState extends ClassLoaderSavedState {
		public boolean scrollToBottom;

		public SavedState(Parcelable superState) {
			super(superState, StickyScrollView.class.getClassLoader());
		}

		public SavedState(Parcel source) {
			super(source, StickyScrollView.class.getClassLoader());
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

	public interface StickyScrollListener {
		public void onMainContentScrolled(View scrollContainer, int position, int oldPosition);

		public void onStickyVisibilityChanged(boolean isStickyHidden);

		public void onScrollViewScrolled(int scroll, int oldScroll, int maxScroll);
	}


	@Override
	public void parallaxViewBy(View view, float multiplier) {
		parallaxViewController.parallaxViewBy(view, multiplier);
	}

	@Override
	public void parallaxViewBy(View view, Transformer transformer, float multiplier) {
		parallaxViewController.parallaxViewBy(view, transformer, multiplier);
	}

	@Override
	public void parallaxViewBackgroundBy(View view, Drawable drawable, float multiplier) {
		parallaxViewController.parallaxViewBackgroundBy(view, drawable, multiplier);
	}

}
