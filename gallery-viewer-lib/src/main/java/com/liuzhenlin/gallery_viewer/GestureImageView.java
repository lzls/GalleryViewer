/*
 * Created on 2018/04/17.
 * Copyright © 2018 刘振林. All rights reserved.
 */

package com.liuzhenlin.gallery_viewer;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.support.annotation.VisibleForTesting;
import android.support.v4.widget.ViewDragHelper;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

/**
 * @author <a href="mailto:2233788867@qq.com">刘振林</a>
 */
public class GestureImageView extends AppCompatImageView implements
        ViewTreeObserver.OnGlobalLayoutListener, ValueAnimator.AnimatorUpdateListener {
    private static final String TAG = "GestureImageView";

    private final GestureDetector mGestureDetector;
    private final ScaleGestureDetector mScaleGestureDetector;

    /** The matrix of the image in this view */
    private final Matrix mImageMatrix = new Matrix();

    /** A float array holds the values of the matrix {@link #mImageMatrix} */
    private final float[] mImageMatrixValues = new float[9];

    /** The initial scale of the image after it was properly adjusted to be shown. */
    private float mImageInitialScale;

    /** The minimum scale that the image can be zoomed out to. */
    private float mImageMinScale;
    /** The maximum scale that the image can be zoomed in to. */
    private float mImageMaxScale;

    /**
     * The ratio of the minimum scale of the image {@link #mImageMinScale}
     * to its initial scale {@link #mImageInitialScale}.
     */
    private static final float RATIO_IMAGE_SCALE_MIN_TO_INITIAL = 1 / 5f;
    /**
     * The ratio of the maximum scale of the image {@link #mImageMaxScale}
     * to its initial scale {@link #mImageInitialScale}.
     */
    private static final float RATIO_IMAGE_SCALE_MAX_TO_INITIAL = 5f / 1f;

    /**
     * Multiples of the maximum scale of the image {@link #mImageMaxScale}, means that
     * the image can be temporarily over-scaled to a scale of
     * {@code mImageMaxScale * IMAGE_OVERSCALE_TIMES_ON_MAXIMIZED} times of its initial scale
     * {@link #mImageInitialScale} by the user.
     */
    private static final float IMAGE_OVERSCALE_TIMES_ON_MAXIMIZED = 1.5f;

    private int mPrivateFlags;

    /**
     * A flag indicates that the initial scale {@link #mImageInitialScale}
     * of the shown image has been resolved.
     */
    private static final int PFLAG_IMAGE_INITIAL_SCALE_RESOLVED = 1;

    /**
     * A flag indicates that the user can scale or translate the image
     * with zoom in and out or drag and drop gestures.
     */
    private static final int PFLAG_IMAGE_GESTURES_ENABLED = 1 << 1;

    /**
     * A flag indicates that the user can translate the image
     * with single finger drag and drop gestures though the image has not been magnified.
     * Note that only when {@link #mPrivateFlags} has been marked with
     * {@link #PFLAG_IMAGE_GESTURES_ENABLED} will it take work.
     */
    private static final int PFLAG_MOVE_UNMAGNIFIED_IMAGE_VIA_SINGLE_FINGER_ALLOWED = 1 << 2;

    /**
     * A flag indicates that the image is being dragged by user
     */
    private static final int PFLAG_IMAGE_BEING_DRAGGED = 1 << 3;

    /**
     * Indicates that we have performed a long click during the user's current touches
     */
    private static final int PFLAG_HAS_PERFORMED_LONG_CLICK = 1 << 4;

    /**
     * Indicates that the performed long click has been consumed
     */
    private static final int PFLAG_LONG_CLICK_CONSUMED = 1 << 5;

    /** The bounds of the image */
    private final RectF mImageBounds = new RectF();

    /** Distance to travel before drag may begin */
    protected final int mTouchSlop;

    /** Last known pointer id for touch events */
    private int mActivePointerId = ViewDragHelper.INVALID_POINTER;

    private float mDownX;
    private float mDownY;

    private final float[] mTouchX = new float[2];
    private final float[] mTouchY = new float[2];

    private VelocityTracker mVelocityTracker;

    /** The minimum velocity for the user gesture to be detected as flying. */
    private final int mFlyingMinimumVelocity; // 400 dp/s
    /** The maximum velocity that a flying gesture can produce. */
    private final int mFlyingMaximumVelocity; // 8000 dp/s

    /**
     * The ratio of the offset (relative to the current position of the image) that the image
     * will be translated by to the current flying velocity.
     */
    private static final float RATIO_FLYING_OFFSET_TO_VELOCITY = 1f / 10f;

    /**
     * The displacement by which this image will be over-translated on we flying it to some end
     * when it is magnified, as measured in pixels.
     */
    private final float mImageOverTranslation; // 25dp

    private float mOverTransX;
    private float mOverTransY;
    private final Runnable mOverTranslateImageRunnable = new Runnable() {
        @Override
        public void run() {
            PointF trans = getImageTranslation();
            animateTranslatingImage(trans, new PointF(
                    trans.x - mOverTransX, trans.y - mOverTransY));
        }
    };

    /**
     * The time interval in milliseconds that {@link #mScaleImageAnimator} or
     * {@link #mTranslateImageAnimator} will last for.
     */
    private static final int DURATION_ANIMATE_IMAGE = 250; // ms

    private ValueAnimator mScaleImageAnimator;
    private float mFinalAnimatedScaleX;
    private float mFinalAnimatedScaleY;

    private ValueAnimator mTranslateImageAnimator;
    private float mLastAnimatedTranslationX;
    private float mLastAnimatedTranslationY;

    /**
     * The scaling pivot point (relative to current view) of the image
     * while {@link #mScaleImageAnimator} is running to scaling it.
     */
    private final PointF mImageScalingPivot = new PointF();

    public GestureImageView(Context context) {
        this(context, null);
    }

    public GestureImageView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GestureImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.GestureImageView, defStyleAttr, 0);
        setImageGesturesEnabled(a.getBoolean(R.styleable
                .GestureImageView_imageGesturesEnabled, true));
        setMoveUnmagnifiedImageViaSingleFingerAllowed(a.getBoolean(R.styleable
                .GestureImageView_moveUnmagnifiedImageViaSingleFingerAllowed, false));
        a.recycle();

        OnImageGestureListener listener = new OnImageGestureListener();
        mGestureDetector = new GestureDetector(context, listener);
        mScaleGestureDetector = new ScaleGestureDetector(context, listener);

        ViewConfiguration vc = ViewConfiguration.get(context);
        mTouchSlop = vc.getScaledTouchSlop();
        mFlyingMaximumVelocity = vc.getScaledMaximumFlingVelocity();
        mFlyingMinimumVelocity = (int) (mFlyingMaximumVelocity / 20f + 0.5f);
        mImageOverTranslation = 25f * context.getResources().getDisplayMetrics().density;
    }

    public boolean isImageGesturesEnabled() {
        return (mPrivateFlags & PFLAG_IMAGE_GESTURES_ENABLED) != 0;
    }

    public void setImageGesturesEnabled(boolean enabled) {
        if (enabled) {
            setScaleType(ScaleType.MATRIX);
            mPrivateFlags |= PFLAG_IMAGE_GESTURES_ENABLED;
        } else {
            mPrivateFlags &= ~PFLAG_IMAGE_GESTURES_ENABLED;
            setScaleType(ScaleType.FIT_CENTER);
        }
    }

    public boolean isMoveUnmagnifiedImageViaSingleFingerAllowed() {
        return (mPrivateFlags & PFLAG_MOVE_UNMAGNIFIED_IMAGE_VIA_SINGLE_FINGER_ALLOWED) != 0;
    }

    public void setMoveUnmagnifiedImageViaSingleFingerAllowed(boolean allowed) {
        if (allowed)
            mPrivateFlags |= PFLAG_MOVE_UNMAGNIFIED_IMAGE_VIA_SINGLE_FINGER_ALLOWED;
        else
            mPrivateFlags &= ~PFLAG_MOVE_UNMAGNIFIED_IMAGE_VIA_SINGLE_FINGER_ALLOWED;
    }

    @Override
    public void setScaleType(ScaleType scaleType) {
        if ((mPrivateFlags & PFLAG_IMAGE_GESTURES_ENABLED) == 0) {
            super.setScaleType(scaleType);
        }
    }

    @VisibleForTesting
    @Override
    public void onGlobalLayout() {
        if ((mPrivateFlags & PFLAG_IMAGE_INITIAL_SCALE_RESOLVED) == 0) {
            mPrivateFlags |= PFLAG_IMAGE_INITIAL_SCALE_RESOLVED;

            Drawable d = getDrawable();
            if (d == null) return;

            // Get the available width and height of the image
            final int width = getWidth() - getPaddingLeft() - getPaddingRight();
            final int height = getHeight() - getPaddingTop() - getPaddingBottom();
            // Get the width and height of the image
            final int imgWidth = d.getIntrinsicWidth();
            final int imgHeight = d.getIntrinsicHeight();

            mImageInitialScale = Math.min((float) width / imgWidth, (float) height / imgHeight);
            mImageMinScale = mImageInitialScale * RATIO_IMAGE_SCALE_MIN_TO_INITIAL;
            mImageMaxScale = mImageInitialScale * RATIO_IMAGE_SCALE_MAX_TO_INITIAL;

            // We need to ensure below will work normally if an other image has been set for this view,
            // so just reset the current matrix to its initial state.
            mImageMatrix.reset();
            // Translate the image to the center of the current view
            mImageMatrix.postTranslate((width - imgWidth) / 2f, (height - imgHeight) / 2f);
            // Proportionally scale the image to make its width equal its available width
            // or/and height equal its available height.
            mImageMatrix.postScale(mImageInitialScale, mImageInitialScale, width / 2f, height / 2f);
            setImageMatrix(mImageMatrix);
        }
    }

    /** Scales the image to its initial size */
    public void rescaleImage() {
        cancelAnimations();

        if ((mPrivateFlags & PFLAG_IMAGE_INITIAL_SCALE_RESOLVED) == 0) {
            onGlobalLayout();
        } else {
            mImageMatrix.setScale(mImageInitialScale, mImageInitialScale);
            PointF transBy = computeImageTranslationByOnScaled(mImageMatrix);
            mImageMatrix.postTranslate(transBy.x, transBy.y);
            setImageMatrix(mImageMatrix);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalLayoutListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN)
            getViewTreeObserver().removeOnGlobalLayoutListener(this);
        else
            getViewTreeObserver().removeGlobalOnLayoutListener(this);
    }

    /**
     * @see AppCompatImageView#setImageResource(int)
     * @see AppCompatImageView#setImageIcon(Icon)
     */
    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        if (getDrawable() != drawable) {
            mPrivateFlags &= ~PFLAG_IMAGE_INITIAL_SCALE_RESOLVED;
            super.setImageDrawable(drawable);
        }
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        Drawable d = getDrawable();
        if (!(d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() == bm)) {
            mPrivateFlags &= ~PFLAG_IMAGE_INITIAL_SCALE_RESOLVED;
            super.setImageBitmap(bm);
        }
    }

    @Override
    public void setImageURI(@Nullable Uri uri) {
        mPrivateFlags &= ~PFLAG_IMAGE_INITIAL_SCALE_RESOLVED;
        super.setImageURI(uri);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Only when the image gestures are enabled and the current view has been set an image
        // can we process the touch events.
        if ((mPrivateFlags & PFLAG_IMAGE_GESTURES_ENABLED) == 0 || getDrawable() == null) {
            return super.onTouchEvent(event);
        }

        if (mGestureDetector.onTouchEvent(event)) // monitor single tap and double tap events
            return true;
        mScaleGestureDetector.onTouchEvent(event); // monitor the scale gestures

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                mPrivateFlags &= ~(PFLAG_IMAGE_BEING_DRAGGED
                        | PFLAG_HAS_PERFORMED_LONG_CLICK | PFLAG_LONG_CLICK_CONSUMED);
            case MotionEvent.ACTION_POINTER_DOWN:
                onPointerDown(event);
                break;
            case MotionEvent.ACTION_MOVE: // translate the image after we handled the touch events.
                if (!onPointerMove(event)) {
                    return false;
                }

                if ((mPrivateFlags & PFLAG_IMAGE_BEING_DRAGGED) == 0) {
                    final float dx = mTouchX[mTouchX.length - 1] - mDownX;
                    final float dy = mTouchY[mTouchY.length - 1] - mDownY;
                    if (Math.abs(dx) > mTouchSlop || Math.abs(dy) > mTouchSlop) {
                        mPrivateFlags |= PFLAG_IMAGE_BEING_DRAGGED;
                        requestParentDisallowInterceptTouchEvent();
                        cancelAnimations();
                        ensureImageMatrix();
                    }
                } else {
                    // If we are allowed to move the image via single finger
                    // when it hasn't been zoomed in, then we can make it translated,
                    // or else it will not be moved unless it has been enlarged
                    // or we are touching it using multiple fingers.
                    if ((mPrivateFlags & PFLAG_MOVE_UNMAGNIFIED_IMAGE_VIA_SINGLE_FINGER_ALLOWED) != 0
                            || getImageScaleX() > mImageInitialScale
                            || getImageScaleY() > mImageInitialScale
                            || event.getPointerCount() > 1) {
                        initVelocityTracker();
                        mVelocityTracker.addMovement(event);

                        final float deltaX = mTouchX[mTouchX.length - 1] - mTouchX[mTouchX.length - 2];
                        final float deltaY = mTouchY[mTouchY.length - 1] - mTouchY[mTouchY.length - 2];
                        mImageMatrix.postTranslate(deltaX, deltaY);
                        setImageMatrix(mImageMatrix);
                    }
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(event);
                break;
            case MotionEvent.ACTION_UP:
                if ((mPrivateFlags & PFLAG_IMAGE_BEING_DRAGGED) == 0
                        && (mPrivateFlags & PFLAG_HAS_PERFORMED_LONG_CLICK) != 0
                        && (mPrivateFlags & PFLAG_LONG_CLICK_CONSUMED) == 0) {
                    performClick();
                }
            case MotionEvent.ACTION_CANCEL:
                mActivePointerId = ViewDragHelper.INVALID_POINTER;
                if ((mPrivateFlags & PFLAG_IMAGE_BEING_DRAGGED) == 0) {
                    break;
                }

                try {
                    final int width = getWidth() - getPaddingLeft() - getPaddingRight();
                    final int height = getHeight() - getPaddingTop() - getPaddingBottom();

                    resolveImageBounds(mImageMatrix);

                    float scaleX = getImageScaleX();
                    float scaleY = getImageScaleY();
                    // If the current scale of the image is larger than the max scale
                    // it can be scaled to, zoom it out to that scale.
                    if (scaleX > mImageMaxScale || scaleY > mImageMaxScale) {
                        Matrix matrix = new Matrix(mImageMatrix);
                        matrix.postScale(mImageMaxScale / scaleX, mImageMaxScale / scaleY,
                                width / 2f, height / 2f);
                        PointF trans = getImageTranslation();
                        PointF finalTrans = computeImageTranslationByOnScaled(matrix);
                        finalTrans.offset(trans.x, trans.y);

                        animateScalingImage(scaleX, scaleY, mImageMaxScale, mImageMaxScale,
                                width / 2f, height / 2f);
                        // Smoothly translate the image by the distance that it will need to be
                        // translated by when the scaling of it is finished.
                        animateTranslatingImage(trans, finalTrans);
                        break;
                        // If the current scale of the image is smaller than its initial scale,
                        // then we need to zoom it in to its initial.
                    } else if (scaleX < mImageInitialScale || scaleY < mImageInitialScale) {
                        Matrix matrix = new Matrix(mImageMatrix);
                        matrix.postScale(mImageInitialScale / scaleX, mImageInitialScale / scaleY,
                                mImageBounds.left, mImageBounds.top);
                        PointF trans = getImageTranslation();
                        PointF finalTrans = computeImageTranslationByOnScaled(matrix);
                        finalTrans.offset(trans.x, trans.y);

                        animateScalingImage(scaleX, scaleY, mImageInitialScale, mImageInitialScale,
                                mImageBounds.left, mImageBounds.top);
                        animateTranslatingImage(trans, finalTrans);
                        break;
                    }

                    if (mVelocityTracker == null) {
                        break;
                    }  // No scaling is needed
                    mVelocityTracker.computeCurrentVelocity(1000, mFlyingMaximumVelocity);
                    final float vx = mVelocityTracker.getXVelocity(mActivePointerId);
                    final float vy = mVelocityTracker.getYVelocity(mActivePointerId);
                    // If one of the velocities is not less than our minimum flying velocity,
                    // treat it as flying on user raising up his/her last finger that is
                    // touching the screen.
                    if ((Math.abs(vx) >= mFlyingMinimumVelocity
                            || Math.abs(vy) >= mFlyingMinimumVelocity)) {
                        final float imgWidth = mImageBounds.width();
                        final float imgHeight = mImageBounds.height();
                        // Only when the width or height of the image is greater than
                        // the width or height of the view can this flying be valid
                        // to translate the image through animator to show its other area
                        // to the user.
                        if (imgWidth > width || imgHeight > height) {
                            float dx = vx * RATIO_FLYING_OFFSET_TO_VELOCITY;
                            float dy = vy * RATIO_FLYING_OFFSET_TO_VELOCITY;
                            /*
                             * Adjust dx and dy to make the image translated under our control
                             * (finally let it fit current view).
                             *
                             * @see #computeImageTranslationByOnScaled(Matrix)
                             */
                            mOverTransX = mOverTransY = 0f;
                            if (imgWidth > width) {
                                if (mImageBounds.left + dx >= 0f) {
                                    if (mImageBounds.left < 0f)
                                        mOverTransX = mImageOverTranslation;
                                    dx = -mImageBounds.left + mOverTransX;
                                } else if (mImageBounds.right + dx <= width) {
                                    if (mImageBounds.right > width)
                                        mOverTransX = -mImageOverTranslation;
                                    dx = width - mImageBounds.right + mOverTransX;
                                }
                            } else {
                                dx = (width + imgWidth) / 2f - mImageBounds.right;
                            }
                            if (imgHeight > height) {
                                if (mImageBounds.top + dy >= 0f) {
                                    if (mImageBounds.top < 0f)
                                        mOverTransY = mImageOverTranslation;
                                    dy = -mImageBounds.top + mOverTransY;
                                } else if (mImageBounds.bottom + dy <= height) {
                                    if (mImageBounds.bottom > height)
                                        mOverTransY = -mImageOverTranslation;
                                    dy = height - mImageBounds.bottom + mOverTransY;
                                }
                            } else {
                                dy = (height + imgHeight) / 2f - mImageBounds.bottom;
                            }
                            if (dx == 0f && dy == 0f) {
                                break;
                            }
                            PointF trans = getImageTranslation();
                            animateTranslatingImage(trans, new PointF(trans.x + dx, trans.y + dy));

                            if (mOverTransX == 0 && mOverTransY == 0) {
                                break;
                            }
                            postDelayed(mOverTranslateImageRunnable, DURATION_ANIMATE_IMAGE);
                            break;
                        }
                    }
                    // Not else!
                    // Here regard it as normal scroll
                    PointF trans = getImageTranslation();
                    PointF finalTrans = computeImageTranslationByOnScaled(mImageMatrix);
                    finalTrans.offset(trans.x, trans.y);
                    animateTranslatingImage(trans, finalTrans);
                    break;
                } finally {
                    recycleVelocityTracker();
                }
        }
        return true;
    }

    private void onPointerDown(MotionEvent e) {
        final int actionIndex = e.getActionIndex();
        mActivePointerId = e.getPointerId(actionIndex);
        mDownX = e.getX(actionIndex);
        mDownY = e.getY(actionIndex);
        markCurrTouchPoint(mDownX, mDownY);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean onPointerMove(MotionEvent e) {
        final int pointerIndex = e.findPointerIndex(mActivePointerId);
        if (pointerIndex < 0) {
            Log.e(TAG, "Error processing scroll; pointer index for id "
                    + mActivePointerId + " not found. Did any MotionEvents get skipped?");
            return false;
        }
        markCurrTouchPoint(e.getX(pointerIndex), e.getY(pointerIndex));
        return true;
    }

    private void onSecondaryPointerUp(MotionEvent e) {
        final int pointerIndex = e.getActionIndex();
        final int pointerId = e.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up.
            // Choose a new active pointer and adjust accordingly.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mActivePointerId = e.getPointerId(newPointerIndex);
            mDownX = e.getX(newPointerIndex);
            mDownY = e.getY(newPointerIndex);
            markCurrTouchPoint(mDownX, mDownY);
        }
    }

    private void markCurrTouchPoint(float x, float y) {
        System.arraycopy(mTouchX, 1, mTouchX, 0, mTouchX.length - 1);
        mTouchX[mTouchX.length - 1] = x;
        System.arraycopy(mTouchY, 1, mTouchY, 0, mTouchY.length - 1);
        mTouchY[mTouchY.length - 1] = y;
    }

    private void requestParentDisallowInterceptTouchEvent() {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
        }
    }

    private void initVelocityTracker() {
        if (mVelocityTracker == null)
            mVelocityTracker = VelocityTracker.obtain();
    }

    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    protected class OnImageGestureListener extends GestureDetector.SimpleOnGestureListener
            implements ScaleGestureDetector.OnScaleGestureListener {

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            performClick();
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            mPrivateFlags |= PFLAG_HAS_PERFORMED_LONG_CLICK;
            final boolean consumed;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                consumed = performLongClick(e.getX(), e.getY());
            } else {
                consumed = performLongClick();
            }
            if (consumed) {
                mPrivateFlags |= PFLAG_LONG_CLICK_CONSUMED;
            }
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            final float lastScaleX = getImageScaleX();
            final float lastScaleY = getImageScaleY();
            final float scaleX, scaleY;
            // If the image has been enlarged, make it zoomed out to its initial scale
            // on user's double tapping.
            if (lastScaleX > mImageInitialScale + 0.01 || lastScaleY > mImageInitialScale + 0.01)
                scaleX = scaleY = mImageInitialScale;
            else // else make it zoomed in
                scaleX = scaleY = mImageMaxScale / 2f;

            final float pivotX = e.getX();
            final float pivotY = e.getY();

            Matrix matrix = new Matrix(getImageMatrix());
            matrix.postScale(scaleX / lastScaleX, scaleY / lastScaleY, pivotX, pivotY);
            PointF trans = getImageTranslation();
            PointF finalTrans = computeImageTranslationByOnScaled(matrix);
            finalTrans.offset(trans.x, trans.y);

            animateScalingImage(lastScaleX, lastScaleY, scaleX, scaleY, pivotX, pivotY);
            animateTranslatingImage(trans, finalTrans);
            return true;
        }

        @Override
        public boolean onDoubleTapEvent(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            if ((mPrivateFlags & PFLAG_IMAGE_BEING_DRAGGED) == 0) {
                mPrivateFlags |= PFLAG_IMAGE_BEING_DRAGGED;
                requestParentDisallowInterceptTouchEvent();
                cancelAnimations();
                // Ensure our matrix for fear that the values of the current matrix
                // of the image might have been changed.
                ensureImageMatrix();
            }
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            final float lastScaleX = getImageScaleX();
            final float lastScaleY = getImageScaleY();

            final float scale = detector.getScaleFactor();
            final float scaleX, scaleY;
            /*
             * Adjust the scaleX and scaleY to make them within the range of the scales
             * that the image can be scaled to.
             */
            final float maxScale = mImageMaxScale * IMAGE_OVERSCALE_TIMES_ON_MAXIMIZED;
            // scaleX
            if (scale * lastScaleX > maxScale) scaleX = maxScale / lastScaleX;
            else if (scale * lastScaleX < mImageMinScale) scaleX = mImageMinScale / lastScaleX;
            else scaleX = scale;
            // scaleY
            if (scale * lastScaleY > maxScale) scaleY = maxScale / lastScaleY;
            else if (scale * lastScaleY < mImageMinScale) scaleY = mImageMinScale / lastScaleY;
            else scaleY = scale;

            mImageMatrix.postScale(scaleX, scaleY, detector.getFocusX(), detector.getFocusY());
            setImageMatrix(mImageMatrix);
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {

        }
    }

    /**
     * Computes the displacement the image will need to be translated by after it is zoomed.
     *
     * @param matrix The matrix that will be finally set for this view
     * @return a {@link PointF} containing the horizontal and the vertical displacements
     */
    public PointF computeImageTranslationByOnScaled(Matrix matrix) {
        final int width = getWidth() - getPaddingLeft() - getPaddingRight();
        final int height = getHeight() - getPaddingTop() - getPaddingBottom();

        float dx = 0f;
        float dy = 0f;
        resolveImageBounds(matrix);
        // If the width of the image is greater than or equal to the width of the view,
        // let it fill the current view horizontally,
        if (mImageBounds.width() >= width) {
            if (mImageBounds.left > 0f) {
                dx = -mImageBounds.left;
            } else if (mImageBounds.right < width) {
                dx = width - mImageBounds.right;
            }
            // else let it be horizontally centered.
        } else {
            dx = (width + mImageBounds.width()) / 2f - mImageBounds.right;
        }
        // If the height of the image is greater than or equal to the height of the view,
        // let it fill the current view vertically,
        if (mImageBounds.height() >= height) {
            if (mImageBounds.top > 0f) {
                dy = -mImageBounds.top;
            } else if (mImageBounds.bottom < height) {
                dy = height - mImageBounds.bottom;
            }
            // else let it be vertically centered.
        } else {
            dy = (height + mImageBounds.height()) / 2f - mImageBounds.bottom;
        }
        return new PointF(dx, dy);
    }

    /**
     * Smoothly scale the image through animator.<br>
     * <b>Note that this can be simultaneously used with
     * {@link #animateTranslatingImage(PointF, PointF)},
     * but you'd better call it before the latter is invoked.</b>
     *
     * @param fromX  the current horizontal scale value of the matrix of the image
     * @param fromY  the current vertical scale value of the matrix of the image
     * @param toX    the horizontal scale that the image will be scaled to
     * @param toY    the vertical scale that the image will be scaled to
     * @param pivotX the x coordinate of the pivot point of the scale transformation
     * @param pivotY the y coordinate of the pivot point of the scale transformation
     */
    public void animateScalingImage(float fromX, float fromY, float toX, float toY,
                                    float pivotX, float pivotY) {
        ensureImageMatrix();
        mFinalAnimatedScaleX = toX;
        mFinalAnimatedScaleY = toY;
        mImageScalingPivot.set(pivotX, pivotY);

        PointF from = new PointF(fromX, fromY);
        PointF to = new PointF(toX, toY);
        if (mScaleImageAnimator == null) {
            mScaleImageAnimator = ValueAnimator.ofObject(new PointFEvaluator(),
                    from, to); // Need to set the values before setting Evaluator
            mScaleImageAnimator.setDuration(DURATION_ANIMATE_IMAGE);
            mScaleImageAnimator.addUpdateListener(this);
        } else {
            mScaleImageAnimator.setObjectValues(from, to);
        }
        mScaleImageAnimator.start();
    }

    /**
     * Smoothly translate the image through animator.<br>
     * <b>Note that this can be simultaneously used with
     * {@link #animateScalingImage(float, float, float, float, float, float)},
     * but you'd better call it immediately after the latter was invoked.</b>
     *
     * @param from the current translation value of the matrix of the image
     * @param to   the final translation value of the matrix of the image
     */
    public void animateTranslatingImage(PointF from, PointF to) {
        ensureImageMatrix();
        mLastAnimatedTranslationX = from.x;
        mLastAnimatedTranslationY = from.y;

        if (mTranslateImageAnimator == null) {
            mTranslateImageAnimator = ValueAnimator.ofObject(new PointFEvaluator(), from, to);
            mTranslateImageAnimator.setDuration(DURATION_ANIMATE_IMAGE);
            mTranslateImageAnimator.addUpdateListener(this);
        } else {
            mTranslateImageAnimator.setObjectValues(from, to);
        }
        mTranslateImageAnimator.start();
    }

    @RestrictTo(RestrictTo.Scope.TESTS)
    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        mImageMatrix.getValues(mImageMatrixValues);

        PointF values = (PointF) animation.getAnimatedValue();
        if (animation == mScaleImageAnimator) {
            mImageMatrix.postScale(values.x / mImageMatrixValues[Matrix.MSCALE_X],
                    values.y / mImageMatrixValues[Matrix.MSCALE_Y],
                    mImageScalingPivot.x, mImageScalingPivot.y);
            setImageMatrix(mImageMatrix);

        } else if (animation == mTranslateImageAnimator) {
            if (mScaleImageAnimator != null && mScaleImageAnimator.isRunning()) {
                mImageMatrix.postTranslate(
                        (values.x - mLastAnimatedTranslationX)
                                * mImageMatrixValues[Matrix.MSCALE_X] / mFinalAnimatedScaleX,
                        (values.y - mLastAnimatedTranslationY)
                                * mImageMatrixValues[Matrix.MSCALE_Y] / mFinalAnimatedScaleY);
            } else {
                mImageMatrix.postTranslate(
                        (values.x - mLastAnimatedTranslationX),
                        (values.y - mLastAnimatedTranslationY));
            }
            setImageMatrix(mImageMatrix);
            mLastAnimatedTranslationX = values.x;
            mLastAnimatedTranslationY = values.y;
        }
    }

    /**
     * Cancel all running animators and the pending animation that will animate
     * over-translating this image
     */
    private void cancelAnimations() {
        // May be that we have scheduled a runnable to over translate this image,
        // which yet hasn't started and should be canceled.
        removeCallbacks(mOverTranslateImageRunnable);
        // Also animators should be canceled if running
        if (mTranslateImageAnimator != null && mTranslateImageAnimator.isRunning()) {
            mTranslateImageAnimator.cancel();
        }
        if (mScaleImageAnimator != null && mScaleImageAnimator.isRunning()) {
            mScaleImageAnimator.cancel();
        }
    }

    /**
     * Ensure our values of the matrix {@link #mImageMatrix} are equal to the values of
     * the current matrix of the image since they may have been changed in other class.
     */
    private void ensureImageMatrix() {
        Matrix matrix = getImageMatrix();
        if (!mImageMatrix.equals(matrix))
            mImageMatrix.set(matrix);
    }

    /**
     * Resolve the image bounds by providing a matrix.
     *
     * @param matrix the matrix used to measure this image
     */
    private void resolveImageBounds(Matrix matrix) {
        Drawable d = getDrawable();
        mImageBounds.set(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        matrix.mapRect(mImageBounds);
    }

    @Nullable
    public RectF getImageBounds() {
        Drawable d = getDrawable();
        if (d != null) {
            RectF imageBounds = new RectF();
            imageBounds.set(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
            getImageMatrix().mapRect(imageBounds);
            return imageBounds;
        }
        return null;
    }

    public float getImageScaleX() {
        getImageMatrix().getValues(mImageMatrixValues);
        return mImageMatrixValues[Matrix.MSCALE_X];
    }

    public float getImageScaleY() {
        getImageMatrix().getValues(mImageMatrixValues);
        return mImageMatrixValues[Matrix.MSCALE_Y];
    }

    public PointF getImageTranslation() {
        getImageMatrix().getValues(mImageMatrixValues);
        return new PointF(mImageMatrixValues[Matrix.MTRANS_X], mImageMatrixValues[Matrix.MTRANS_Y]);
    }
}
