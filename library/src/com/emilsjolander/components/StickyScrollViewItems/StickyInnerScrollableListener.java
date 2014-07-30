package com.emilsjolander.components.StickyScrollViewItems;

import android.view.View;

/**
 * Created by Shad on 28.07.14.
 */
public interface StickyInnerScrollableListener {

	public void onScrollableFling(View v, int t, int oldT, float velocity);

	public void onScrollableScroll(View v, int t, int oldT);

}
