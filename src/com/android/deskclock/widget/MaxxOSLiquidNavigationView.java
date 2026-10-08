/*
 * Copyright (C) 2026 MaxxOS. All rights reserved.
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

package com.android.deskclock.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.core.graphics.ColorUtils;

import com.android.deskclock.TimeOfDayTheme;

/**
 * MaxxOS "liquid glass" bottom navigation.
 *
 * A translucent glass bubble sits behind the selected tab. When the selection changes the
 * bubble stretches like a liquid drop (leading edge moves first, trailing edge follows) and
 * magnifies the icon/label that are inside it like a lens. Pressing and dragging on the bar
 * grows the bubble and lets the finger slide it between tabs.
 */
public class MaxxOSLiquidNavigationView extends BottomNavigationView {

    private static final float LENS_IDLE = 1.10f;
    private static final float LENS_ACTIVE = 1.32f;

    private final float mDp;
    private final int mTouchSlop;

    private final RectF mBubble = new RectF();
    private final RectF mItemRect = new RectF();
    private final Rect mTmp = new Rect();
    private final Path mPath = new Path();
    private final Matrix mShaderMatrix = new Matrix();
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Shader mFillShader;

    private float mLeft;
    private float mRight;
    private float mItemTop;
    private float mItemBottom;
    private float mTargetLeft;
    private float mTargetRight;
    private int mTrackedId;
    private boolean mHasBubble;

    private float mLens = LENS_IDLE;
    private float mPress;
    private boolean mPressed;
    private float mDownX;

    private ValueAnimator mLeftAnim;
    private ValueAnimator mRightAnim;
    private ValueAnimator mLensAnim;
    private ValueAnimator mPressAnim;

    public MaxxOSLiquidNavigationView(Context context) {
        this(context, null);
    }

    public MaxxOSLiquidNavigationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDp = context.getResources().getDisplayMetrics().density;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        // Glass resolved from the theme: white sheen on dark surfaces, dark sheen on
        // light ones, so the bubble reads in both modes. Recreated with the activity
        // on every theme change, so resolving once here is enough.
        final int glass = TimeOfDayTheme.isLightMode(context)
                ? 0xFF000000 : 0xFFFFFFFF;
        mFillShader = new LinearGradient(0f, 0f, 0f, 1f,
                new int[] {
                        ColorUtils.setAlphaComponent(glass, 0x70),
                        ColorUtils.setAlphaComponent(glass, 0x1A),
                        ColorUtils.setAlphaComponent(glass, 0x45) },
                new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        mFill.setStyle(Paint.Style.FILL);
        mFill.setShader(mFillShader);

        mRim.setStyle(Paint.Style.STROKE);
        mRim.setStrokeWidth(1.5f * mDp);
        mRim.setColor(ColorUtils.setAlphaComponent(glass, 0xA0));

        setWillNotDraw(false);
    }

    // ---------------------------------------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------------------------------------

    @Override
    protected void dispatchDraw(Canvas canvas) {
        updateTarget();
        if (!mHasBubble) {
            super.dispatchDraw(canvas);
            return;
        }

        final float width = mRight - mLeft;
        final float baseWidth = Math.max(1f, mTargetRight - mTargetLeft);
        final float stretch = Math.min(1f, Math.max(0f, width / baseWidth - 1f));
        final float grow = mPress * 3f * mDp;
        final float pad = 6f * mDp + stretch * 5f * mDp - grow;
        mBubble.set(mLeft - grow, mItemTop + pad, mRight + grow, mItemBottom - pad);
        final float radius = mBubble.height() / 2f;

        mPath.reset();
        mPath.addRoundRect(mBubble, radius, radius, Path.Direction.CW);

        mShaderMatrix.setScale(1f, mBubble.height());
        mShaderMatrix.postTranslate(0f, mBubble.top);
        mFillShader.setLocalMatrix(mShaderMatrix);
        mFill.setShader(mFillShader);

        // 1) Normal tabs everywhere except under the bubble.
        int save = canvas.save();
        canvas.clipOutPath(mPath);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);

        // 2) Glass body.
        canvas.drawRoundRect(mBubble, radius, radius, mFill);

        // 3) Lens: tabs drawn again, magnified, only inside the bubble.
        save = canvas.save();
        canvas.clipPath(mPath);
        canvas.scale(mLens, mLens, mBubble.centerX(), mBubble.centerY());
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);

        // 4) Glass rim.
        canvas.drawRoundRect(mBubble, radius, radius, mRim);
    }

    private boolean itemBounds(int id, RectF out) {
        final View v = findViewById(id);
        if (v == null || v.getWidth() == 0 || v.getHeight() == 0) {
            return false;
        }
        mTmp.set(0, 0, v.getWidth(), v.getHeight());
        offsetDescendantRectToMyCoords(v, mTmp);
        out.set(mTmp);
        return true;
    }

    private void updateTarget() {
        final int id = getSelectedItemId();
        if (id == 0 || !itemBounds(id, mItemRect)) {
            return;
        }
        final float inset = 2f * mDp;
        final float l = mItemRect.left + inset;
        final float r = mItemRect.right - inset;
        mItemTop = mItemRect.top;
        mItemBottom = mItemRect.bottom;

        if (!mHasBubble) {
            mLeft = l;
            mRight = r;
            mTargetLeft = l;
            mTargetRight = r;
            mTrackedId = id;
            mHasBubble = true;
        } else if (id != mTrackedId) {
            mTrackedId = id;
            mTargetLeft = l;
            mTargetRight = r;
            startMove(l, r);
        } else if (l != mTargetLeft || r != mTargetRight) {
            mTargetLeft = l;
            mTargetRight = r;
            if (!isMoving()) {
                mLeft = l;
                mRight = r;
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Animation
    // ---------------------------------------------------------------------------------------

    private boolean isMoving() {
        return (mLeftAnim != null && mLeftAnim.isRunning())
                || (mRightAnim != null && mRightAnim.isRunning());
    }

    private void startMove(float newLeft, float newRight) {
        if (mLeftAnim != null) {
            mLeftAnim.cancel();
        }
        if (mRightAnim != null) {
            mRightAnim.cancel();
        }
        final boolean toRight = newLeft > mLeft;

        mLeftAnim = ValueAnimator.ofFloat(mLeft, newLeft);
        mRightAnim = ValueAnimator.ofFloat(mRight, newRight);
        mLeftAnim.addUpdateListener(a -> {
            mLeft = (Float) a.getAnimatedValue();
            invalidate();
        });
        mRightAnim.addUpdateListener(a -> {
            mRight = (Float) a.getAnimatedValue();
            invalidate();
        });

        final ValueAnimator leading = toRight ? mRightAnim : mLeftAnim;
        final ValueAnimator trailing = toRight ? mLeftAnim : mRightAnim;
        leading.setDuration(300);
        leading.setInterpolator(new OvershootInterpolator(1.3f));
        trailing.setDuration(480);
        trailing.setInterpolator(new PathInterpolator(0.25f, 0.1f, 0.2f, 1f));
        trailing.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!mPressed) {
                    animateLens(LENS_IDLE, 280);
                }
            }
        });

        animateLens(LENS_ACTIVE, 150);
        mLeftAnim.start();
        mRightAnim.start();
    }

    private void animateLens(float to, long duration) {
        if (mLensAnim != null) {
            mLensAnim.cancel();
        }
        mLensAnim = ValueAnimator.ofFloat(mLens, to);
        mLensAnim.setDuration(duration);
        mLensAnim.setInterpolator(new DecelerateInterpolator());
        mLensAnim.addUpdateListener(a -> {
            mLens = (Float) a.getAnimatedValue();
            invalidate();
        });
        mLensAnim.start();
    }

    private void setPressedState(boolean pressed) {
        if (mPressed == pressed) {
            return;
        }
        mPressed = pressed;
        if (mPressAnim != null) {
            mPressAnim.cancel();
        }
        mPressAnim = ValueAnimator.ofFloat(mPress, pressed ? 1f : 0f);
        mPressAnim.setDuration(pressed ? 180 : 260);
        mPressAnim.setInterpolator(pressed ? new OvershootInterpolator(1.5f)
                : new DecelerateInterpolator());
        mPressAnim.addUpdateListener(a -> {
            mPress = (Float) a.getAnimatedValue();
            invalidate();
        });
        mPressAnim.start();
        animateLens(pressed ? LENS_ACTIVE : LENS_IDLE, 180);
    }

    // ---------------------------------------------------------------------------------------
    // Touch: press grows the bubble, dragging slides it between tabs.
    // ---------------------------------------------------------------------------------------

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = ev.getX();
                setPressedState(true);
                break;
            case MotionEvent.ACTION_MOVE:
                if (mPressed && Math.abs(ev.getX() - mDownX) > mTouchSlop) {
                    dragTo(ev.getX());
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                setPressedState(false);
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    private void dragTo(float x) {
        final Menu menu = getMenu();
        for (int i = 0; i < menu.size(); i++) {
            final MenuItem item = menu.getItem(i);
            if (!item.isEnabled()) {
                continue;
            }
            final RectF r = new RectF();
            if (itemBounds(item.getItemId(), r) && x >= r.left && x <= r.right) {
                if (item.getItemId() != getSelectedItemId()) {
                    setSelectedItemId(item.getItemId());
                }
                return;
            }
        }
    }
}
