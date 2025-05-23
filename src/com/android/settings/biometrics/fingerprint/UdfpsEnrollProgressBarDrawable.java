/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.biometrics.fingerprint;

import static org.sun.os.CustomVibrationAttributes.VIBRATION_ATTRIBUTES_FINGERPRINT_UNLOCK;

import static vendor.sun.hardware.vibratorExt.Effect.CLICK;
import static vendor.sun.hardware.vibratorExt.Effect.DOUBLE_CLICK;
import static vendor.sun.hardware.vibratorExt.Effect.UNIFIED_ERROR;
import static vendor.sun.hardware.vibratorExt.Effect.UNIFIED_SUCCESS;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.VibrationAttributes;
import android.os.VibrationExtInfo;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.settings.R;

/**
 * UDFPS enrollment progress bar.
 */
public class UdfpsEnrollProgressBarDrawable extends Drawable {
    private static final String TAG = "UdfpsProgressBar";

    private static final long CHECKMARK_ANIMATION_DELAY_MS = 200L;
    private static final long CHECKMARK_ANIMATION_DURATION_MS = 300L;
    private static final long FILL_COLOR_ANIMATION_DURATION_MS = 350L;
    private static final long PROGRESS_ANIMATION_DURATION_MS = 400L;
    private static final float STROKE_WIDTH_DP = 12f;
    private static final Interpolator DEACCEL = new DecelerateInterpolator();

    private static final VibrationAttributes HARDWARE_FEEDBACK_VIBRATION_ATTRIBUTES =
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK);

    private final float mStrokeWidthPx;
    @ColorInt
    private final int mProgressColor;
    @ColorInt
    private final int mHelpColor;
    @ColorInt
    private final int mOnFirstBucketFailedColor;
    @NonNull
    private final Drawable mCheckmarkDrawable;
    @NonNull
    private final Interpolator mCheckmarkInterpolator;
    @NonNull
    private final Paint mBackgroundPaint;
    @VisibleForTesting
    @NonNull
    final Paint mFillPaint;
    @NonNull
    private final Vibrator mVibrator;
    @NonNull
    private final boolean mIsAccessibilityEnabled;
    @NonNull
    private final Context mContext;

    private boolean mAfterFirstTouch;

    private int mRemainingSteps = 0;
    private int mTotalSteps = 0;
    private float mProgress = 0f;
    @Nullable
    private ValueAnimator mProgressAnimator;
    @NonNull
    private final ValueAnimator.AnimatorUpdateListener mProgressUpdateListener;

    private boolean mShowingHelp = false;
    @Nullable
    private ValueAnimator mFillColorAnimator;
    @NonNull
    private final ValueAnimator.AnimatorUpdateListener mFillColorUpdateListener;

    @Nullable
    private ValueAnimator mBackgroundColorAnimator;
    @NonNull
    private final ValueAnimator.AnimatorUpdateListener mBackgroundColorUpdateListener;

    private boolean mComplete = false;
    private float mCheckmarkScale = 0f;
    @Nullable
    private ValueAnimator mCheckmarkAnimator;
    @NonNull
    private final ValueAnimator.AnimatorUpdateListener mCheckmarkUpdateListener;

    private int mMovingTargetFill;
    private int mMovingTargetFillError;
    private int mEnrollProgress;
    private int mEnrollProgressHelp;
    private int mEnrollProgressHelpWithTalkback;

    public UdfpsEnrollProgressBarDrawable(@NonNull Context context, @Nullable AttributeSet attrs) {
        mContext = context;

        loadResources(context, attrs);
        float density = context.getResources().getDisplayMetrics().densityDpi;
        mStrokeWidthPx = STROKE_WIDTH_DP * (density / DisplayMetrics.DENSITY_DEFAULT);
        mProgressColor = mEnrollProgress;
        final AccessibilityManager am = context.getSystemService(AccessibilityManager.class);
        mIsAccessibilityEnabled = am.isTouchExplorationEnabled();
        mOnFirstBucketFailedColor = mMovingTargetFillError;
        if (!mIsAccessibilityEnabled) {
            mHelpColor = mEnrollProgressHelp;
        } else {
            mHelpColor = mEnrollProgressHelpWithTalkback;
        }
        mCheckmarkDrawable = context.getDrawable(R.drawable.udfps_enroll_checkmark);
        mCheckmarkDrawable.mutate();
        mCheckmarkInterpolator = new OvershootInterpolator();

        mBackgroundPaint = new Paint();
        mBackgroundPaint.setStrokeWidth(mStrokeWidthPx);
        mBackgroundPaint.setColor(mMovingTargetFill);
        mBackgroundPaint.setAntiAlias(true);
        mBackgroundPaint.setStyle(Paint.Style.STROKE);
        mBackgroundPaint.setStrokeCap(Paint.Cap.ROUND);

        // Progress fill should *not* use the extracted system color.
        mFillPaint = new Paint();
        mFillPaint.setStrokeWidth(mStrokeWidthPx);
        mFillPaint.setColor(mProgressColor);
        mFillPaint.setAntiAlias(true);
        mFillPaint.setStyle(Paint.Style.STROKE);
        mFillPaint.setStrokeCap(Paint.Cap.ROUND);

        mVibrator = mContext.getSystemService(Vibrator.class);

        mProgressUpdateListener = animation -> {
            mProgress = (float) animation.getAnimatedValue();
            invalidateSelf();
        };

        mFillColorUpdateListener = animation -> {
            mFillPaint.setColor((int) animation.getAnimatedValue());
            invalidateSelf();
        };

        mCheckmarkUpdateListener = animation -> {
            mCheckmarkScale = (float) animation.getAnimatedValue();
            invalidateSelf();
        };

        mBackgroundColorUpdateListener = animation -> {
            mBackgroundPaint.setColor((int) animation.getAnimatedValue());
            invalidateSelf();
        };
    }

    void onEnrollmentProgress(int remaining, int totalSteps) {
        mAfterFirstTouch = true;
        updateState(remaining, totalSteps, false /* showingHelp */);
    }

    void onEnrollmentHelp(int remaining, int totalSteps) {
        updateState(remaining, totalSteps, true /* showingHelp */);
    }

    void onLastStepAcquired() {
        updateState(0, mTotalSteps, false /* showingHelp */);
    }

    private void updateState(int remainingSteps, int totalSteps, boolean showingHelp) {
        updateProgress(remainingSteps, totalSteps, showingHelp);
        updateFillColor(showingHelp);
    }

    private void updateProgress(int remainingSteps, int totalSteps, boolean showingHelp) {
        if (mRemainingSteps == remainingSteps && mTotalSteps == totalSteps) {
            return;
        }

        mShowingHelp = showingHelp;
        if (mShowingHelp) {
            if (mVibrator != null && mIsAccessibilityEnabled) {
                mVibrator.vibrateExt(new VibrationExtInfo.Builder()
                        .setEffectId(UNIFIED_ERROR)
                        .setFallbackEffectId(DOUBLE_CLICK)
                        .setReason(getClass().getSimpleName() + "::onEnrollmentHelp")
                        .setVibrationAttributes(HARDWARE_FEEDBACK_VIBRATION_ATTRIBUTES)
                        .build()
                );
            }
        } else {
            // If the first touch is an error, remainingSteps will be -1 and the callback
            // doesn't come from onEnrollmentHelp. If we are in the accessibility flow,
            // we still would like to vibrate.
            if (mVibrator != null) {
                if (remainingSteps == -1 && mIsAccessibilityEnabled) {
                    mVibrator.vibrateExt(new VibrationExtInfo.Builder()
                            .setEffectId(UNIFIED_ERROR)
                            .setFallbackEffectId(DOUBLE_CLICK)
                            .setReason(getClass().getSimpleName() + "::onFirstTouchError")
                            .setVibrationAttributes(HARDWARE_FEEDBACK_VIBRATION_ATTRIBUTES)
                            .build()
                    );
                } else if (remainingSteps != -1 && !mIsAccessibilityEnabled) {
                    mVibrator.vibrateExt(new VibrationExtInfo.Builder()
                            .setEffectId(UNIFIED_SUCCESS)
                            .setFallbackEffectId(CLICK)
                            .setReason(getClass().getSimpleName() + "::OnEnrollmentProgress")
                            .setVibrationAttributes(VIBRATION_ATTRIBUTES_FINGERPRINT_UNLOCK)
                            .build()
                    );
                }
            }
        }

        mRemainingSteps = remainingSteps;
        mTotalSteps = totalSteps;

        final int progressSteps = Math.max(0, totalSteps - remainingSteps);

        // If needed, add 1 to progress and total steps to account for initial touch.
        final int adjustedSteps = mAfterFirstTouch ? progressSteps + 1 : progressSteps;
        final int adjustedTotal = mAfterFirstTouch ? mTotalSteps + 1 : mTotalSteps;

        final float targetProgress = Math.min(1f, (float) adjustedSteps / (float) adjustedTotal);

        if (mProgressAnimator != null && mProgressAnimator.isRunning()) {
            mProgressAnimator.cancel();
        }

        mProgressAnimator = ValueAnimator.ofFloat(mProgress, targetProgress);
        mProgressAnimator.setDuration(PROGRESS_ANIMATION_DURATION_MS);
        mProgressAnimator.addUpdateListener(mProgressUpdateListener);
        mProgressAnimator.start();

        if (remainingSteps == 0) {
            startCompletionAnimation();
        } else if (remainingSteps > 0) {
            rollBackCompletionAnimation();
        }
    }

    private void animateBackgroundColor() {
        if (mBackgroundColorAnimator != null && mBackgroundColorAnimator.isRunning()) {
            mBackgroundColorAnimator.end();
        }
        mBackgroundColorAnimator = ValueAnimator.ofArgb(mBackgroundPaint.getColor(),
                mOnFirstBucketFailedColor);
        mBackgroundColorAnimator.setDuration(FILL_COLOR_ANIMATION_DURATION_MS);
        mBackgroundColorAnimator.setRepeatCount(1);
        mBackgroundColorAnimator.setRepeatMode(ValueAnimator.REVERSE);
        mBackgroundColorAnimator.setInterpolator(DEACCEL);
        mBackgroundColorAnimator.addUpdateListener(mBackgroundColorUpdateListener);
        mBackgroundColorAnimator.start();
    }

    private void updateFillColor(boolean showingHelp) {
        if (!mAfterFirstTouch && showingHelp) {
            // If we are on the first touch, animate the background color
            // instead of the progress color.
            animateBackgroundColor();
            return;
        }

        if (mFillColorAnimator != null && mFillColorAnimator.isRunning()) {
            mFillColorAnimator.end();
        }

        @ColorInt final int targetColor = showingHelp ? mHelpColor : mProgressColor;
        mFillColorAnimator = ValueAnimator.ofArgb(mFillPaint.getColor(), targetColor);
        mFillColorAnimator.setDuration(FILL_COLOR_ANIMATION_DURATION_MS);
        mFillColorAnimator.setRepeatCount(1);
        mFillColorAnimator.setRepeatMode(ValueAnimator.REVERSE);
        mFillColorAnimator.setInterpolator(DEACCEL);
        mFillColorAnimator.addUpdateListener(mFillColorUpdateListener);
        mFillColorAnimator.start();
    }

    private void startCompletionAnimation() {
        if (mComplete) {
            return;
        }
        mComplete = true;

        if (mCheckmarkAnimator != null && mCheckmarkAnimator.isRunning()) {
            mCheckmarkAnimator.cancel();
        }

        mCheckmarkAnimator = ValueAnimator.ofFloat(mCheckmarkScale, 1f);
        mCheckmarkAnimator.setStartDelay(CHECKMARK_ANIMATION_DELAY_MS);
        mCheckmarkAnimator.setDuration(CHECKMARK_ANIMATION_DURATION_MS);
        mCheckmarkAnimator.setInterpolator(mCheckmarkInterpolator);
        mCheckmarkAnimator.addUpdateListener(mCheckmarkUpdateListener);
        mCheckmarkAnimator.start();
    }

    private void rollBackCompletionAnimation() {
        if (!mComplete) {
            return;
        }
        mComplete = false;

        // Adjust duration based on how much of the completion animation has played.
        final float animatedFraction = mCheckmarkAnimator != null
                ? mCheckmarkAnimator.getAnimatedFraction()
                : 0f;
        final long durationMs = Math.round(CHECKMARK_ANIMATION_DELAY_MS * animatedFraction);

        if (mCheckmarkAnimator != null && mCheckmarkAnimator.isRunning()) {
            mCheckmarkAnimator.cancel();
        }

        mCheckmarkAnimator = ValueAnimator.ofFloat(mCheckmarkScale, 0f);
        mCheckmarkAnimator.setDuration(durationMs);
        mCheckmarkAnimator.addUpdateListener(mCheckmarkUpdateListener);
        mCheckmarkAnimator.start();
    }

    private void loadResources(Context context, @Nullable AttributeSet attrs) {
        final TypedArray ta = context.obtainStyledAttributes(attrs,
                R.styleable.BiometricsEnrollView, R.attr.biometricsEnrollStyle,
                R.style.BiometricsEnrollStyle);
        mMovingTargetFill = ta.getColor(
                R.styleable.BiometricsEnrollView_biometricsMovingTargetFill, 0);
        mMovingTargetFillError = ta.getColor(
                R.styleable.BiometricsEnrollView_biometricsMovingTargetFillError, 0);
        mEnrollProgress = ta.getColor(
                R.styleable.BiometricsEnrollView_biometricsEnrollProgress, 0);
        mEnrollProgressHelp = ta.getColor(
                R.styleable.BiometricsEnrollView_biometricsEnrollProgressHelp, 0);
        mEnrollProgressHelpWithTalkback = ta.getColor(
                R.styleable.BiometricsEnrollView_biometricsEnrollProgressHelpWithTalkback, 0);
        ta.recycle();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        canvas.save();

        // Progress starts from the top, instead of the right
        canvas.rotate(-90f, getBounds().centerX(), getBounds().centerY());

        final float halfPaddingPx = mStrokeWidthPx / 2f;

        if (mProgress < 1f) {
            // Draw the background color of the progress circle.
            canvas.drawArc(
                    halfPaddingPx,
                    halfPaddingPx,
                    getBounds().right - halfPaddingPx,
                    getBounds().bottom - halfPaddingPx,
                    0f /* startAngle */,
                    360f /* sweepAngle */,
                    false /* useCenter */,
                    mBackgroundPaint);
        }

        if (mProgress > 0f) {
            // Draw the filled portion of the progress circle.
            canvas.drawArc(
                    halfPaddingPx,
                    halfPaddingPx,
                    getBounds().right - halfPaddingPx,
                    getBounds().bottom - halfPaddingPx,
                    0f /* startAngle */,
                    360f * mProgress /* sweepAngle */,
                    false /* useCenter */,
                    mFillPaint);
        }

        canvas.restore();

        if (mCheckmarkScale > 0f) {
            final float offsetScale = (float) Math.sqrt(2) / 2f;
            final float centerXOffset = (getBounds().width() - mStrokeWidthPx) / 2f * offsetScale;
            final float centerYOffset = (getBounds().height() - mStrokeWidthPx) / 2f * offsetScale;
            final float centerX = getBounds().centerX() + centerXOffset;
            final float centerY = getBounds().centerY() + centerYOffset;

            final float boundsXOffset =
                    mCheckmarkDrawable.getIntrinsicWidth() / 2f * mCheckmarkScale;
            final float boundsYOffset =
                    mCheckmarkDrawable.getIntrinsicHeight() / 2f * mCheckmarkScale;

            final int left = Math.round(centerX - boundsXOffset);
            final int top = Math.round(centerY - boundsYOffset);
            final int right = Math.round(centerX + boundsXOffset);
            final int bottom = Math.round(centerY + boundsYOffset);
            mCheckmarkDrawable.setBounds(left, top, right, bottom);
            mCheckmarkDrawable.draw(canvas);
        }
    }

    @Override
    public void setAlpha(int alpha) {}

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return 0;
    }
}
