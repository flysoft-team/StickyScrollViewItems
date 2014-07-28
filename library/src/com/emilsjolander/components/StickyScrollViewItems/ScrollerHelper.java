package com.emilsjolander.components.StickyScrollViewItems;

import android.content.Context;
import android.hardware.SensorManager;
import android.view.ViewConfiguration;

/**
 * Created by Shad on 28.07.14.
 */
public class ScrollerHelper {
	private static float DECELERATION_RATE = (float) (Math.log(0.78) / Math.log(0.9));
	private static final float INFLEXION = 0.35f;
	private static float mFlingFriction = ViewConfiguration.getScrollFriction();
	private float mPhysicalCoeff;

	public ScrollerHelper(Context context)
	{
		final float ppi = context.getResources().getDisplayMetrics().density * 160.0f;
		mPhysicalCoeff = SensorManager.GRAVITY_EARTH // g (m/s^2)
				* 39.37f // inch/meter
				* ppi
				* 0.84f;
	}

	private  float getSplineDeceleration(int velocity) {
		return (float) Math.log(INFLEXION * Math.abs(velocity) / (mFlingFriction * mPhysicalCoeff));
	}

	public int getSplineFlingDistance(int velocity) {
		final double l = getSplineDeceleration(velocity);
		final double decelMinusOne = DECELERATION_RATE - 1.0;
		float totalDistance= (float) (mFlingFriction * mPhysicalCoeff * Math.exp(DECELERATION_RATE / decelMinusOne *
						l));
//		return (int) totalDistance;
		return (int) (totalDistance * Math.signum(velocity));
	}

	/* Returns the duration, expressed in milliseconds */
	public  int getSplineFlingDuration(int velocity) {
		final double l = getSplineDeceleration(velocity);
		final double decelMinusOne = DECELERATION_RATE - 1.0;
		return (int) (1000.0 * Math.exp(l / decelMinusOne));
	}
}
