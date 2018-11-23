/*
 * Created on 2018/04/17.
 * Copyright © 2018 刘振林. All rights reserved.
 */

package com.liuzhenlin.gallery_viewer;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.Nullable;
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

/**
 * @author <a href="mailto:2233788867@qq.com">刘振林</a>
 */
public class GestureImageView extends AppCompatImageView {
    private static final String TAG = "GestureImageView";
    private static final boolean DEBUG = false;

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
     * A multiplier of the maximum scale for the image {@link #mImageMaxScale}, means that
     * the image can be temporarily over-scaled to a scale
     * {@value #IMAGE_OVERSCALE_TIMES_ON_MAXIMIZED} times the maximum one by the user.
     */
    @SuppressWarnings("JavaDoc")
    private static final float IMAGE_OVERSCALE_TIMES_ON_MAXIMIZED = 1.5f;

    private int mPrivateFlags;

    /**
     * A flag indicates that the user can scale or translate the image with zoom in and out
     * or drag and drop gestures.
     */
    private static final int PFLAG_IMAGE_GESTURES_ENABLED = 1;

    /**
     * A flag indicates that the user can translate the image
     * with single finger drag and drop gestures though the image has not been magnified.
     * <p>
     * <strong>NOTE:</strong> Only when {@link #mPrivateFlags} has been marked with
     * {@link #PFLAG_IMAGE_GESTURES_ENABLED} will it take work.
     */
    private static final int PFLAG_MOVE_UNMAGNIFIED_IMAGE_VIA_SINGLE_FINGER_ALLOWED = 1 << 1;

    /**
     * A flag indicates that the image is being dragged by user
     */
    private static final int PFLAG_IMAGE_BEING_DRAGGED = 1 << 2;

    /**
     * Indicates that we have performed a long click during the user's current touches
     */
    private static final int PFLAG_HAS_PERFORMED_LONG_CLICK = 1 << 3;

    /**
     * Indicates that the performed long click has been consumed
     */
    private static final int PFLAG_LONG_CLICK_CONSUMED = 1 << 4;

    /** The bounds of the image */
    private final RectF mImageBounds = new RectF();

    /** Square of the distance to travel before drag may begin */
    protected final int mTouchSlopSquare;

    /** Last known pointer id for touch events */
    private int mActivePointerId = ViewDragHelper.INVALID_POINTER;

    private float mDownX;
    private float mDownY;

    private final float[] mTouchX = new float[2];
    private final float[] mTouchY = new float[2];

    private VelocityTracker mVelocityTracker;

    /** The minimum velocity for the user gesture to be detected as fling. */
    private final int mMinimumFlingVelocity; // 400 dp/s
    /** The maximum velocity that a fling gesture can produce. */
    private final int mMaximumFlingVelocity; // 8000 dp/s

    /**
     * The ratio of the offset (relative to the current position of the image) that the image
     * will be translated by to the current fling velocity.
     */
    private static final float RATIO_FLING_OFFSET_TO_VELOCITY = 1f / 10f;

    /**
     * The displacement by which this magnified image will be over-translated when we fling it
     * to some end, as measured in pixels.
     */
    private final float mImageOverTranslation; // 25dp

    private float mOverTranslationX;
    private float mOverTranslationY;
    private final Runnable mImageSpringBackRunnable = new Runnable() {
        @Override
        public void run() {
            PointF trans = getImageTranslation();
            animateTranslatingImage(trans, new PointF(
                    trans.x - mOverTranslationX, trans.y - mOverTranslationY));
        }
    };

    /**
     * Time interval in milliseconds that {@link #mScaleImageAnimator} or
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
     * while {@link #mScaleImageAnimator} is running to scale it.
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
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.GestureImageView, defStyleAttr, 0);
        setImageGesturesEnabled(ta.getBoolean(R.styleable
                .GestureImageView_imageGesturesEnabled, true));
        setMoveUnmagnifiedImageViaSingleFingerAllowed(ta.getBoolean(R.styleable
                .GestureImageView_moveUnmagnifiedImageViaSingleFingerAllowed, false));
        ta.recycle();

        OnImageGestureListener listener = new OnImageGestureListener();
        mGestureDetector = new GestureDetector(context, listener);
        mScaleGestureDetector = new ScaleGestureDetector(context, listener);

        ViewConfiguration vc = ViewConfiguration.get(context);
        final int touchSlop = vc.getScaledTouchSlop();
        mTouchSlopSquare = touchSlop * touchSlop;
        mMaximumFlingVelocity = vc.getScaledMaximumFlingVelocity();
        mMinimumFlingVelocity = (int) (mMaximumFlingVelocity / 20f + 0.5f);
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

    /**
     * Scales the image to fit current view and translates it to the center of this view.
     */
    private void initializeImage() {
        Drawable d = getDrawable();
        if (d == null) return;

        // Get the available width and height for the image
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

    /**
     * Rescales the image to its initial size and moves it back to this view's center.
     */
    public void reinitializeImage() {
        cancelAnimations();
        initializeImage();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (DEBUG) {
            //@formatter:off
            Log.i(TAG, "Size of GestureImageView changes: "
                    + "oldw= " + oldw + "   " + "oldh= " + oldh + "   "
                    + "w= "    + w    + "   " + "h= "    + h);
            //@formatter:on
        }
        if (oldw == 0 && oldh == 0 /* This view is just added to the view hierarchy */) {
            initializeImage();
        } else {
            reinitializeImage();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Only when the image gestures are enabled and the current view has been set an image
        // can we process the touch events.
        if ((mPrivateFlags & PFLAG_IMAGE_GESTURES_ENABLED) == 0 || getDrawable() == null) {
            clearTouch();
            return super.onTouchEvent(event);
        }

        if (mGestureDetector.onTouchEvent(event)) // Monitor single tap and double tap events
            return true;
        mScaleGestureDetector.onTouchEvent(event); // Monitor the scale gestures

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                // Ensures the touch caches are in the initial state when a new gesture starts.
                resetTouch();
            case MotionEvent.ACTION_POINTER_DOWN:
                onPointerDown(event);
                break;

            case MotionEvent.ACTION_MOVE:
                if (!onPointerMove(event)) {
                    return false;
                }

                if ((mPrivateFlags & PFLAG_IMAGE_BEING_DRAGGED) == 0) {
                    final float absDX = Math.abs(mTouchX[mTouchX.length - 1] - mDownX);
                    final float absDY = Math.abs(mTouchY[mTouchY.length - 1] - mDownY);
                    if (absDX * absDX + absDY * absDY > mTouchSlopSquare) {
                        mPrivateFlags |= PFLAG_IMAGE_BEING_DRAGGED;
                        requestParentDisallowInterceptTouchEvent();
                        cancelAnimations();
                        ensureImageMatrix();
                    }
                } else {
                    // If we are allowed to move the image via single finger when it hasn't been
                    // zoomed in, then we can make it translated, or else it will not be moved
                    // unless it has been enlarged or we are touching it using multiple fingers.
                    if ((mPrivateFlags & PFLAG_MOVE_UNMAGNIFIED_IMAGE_VIA_SINGLE_FINGER_ALLOWED) != 0
                            || getImageScaleX() > mImageInitialScale
                            || getImageScaleY() > mImageInitialScale
                            || event.getPointerCount() > 1) {
                        if (mVelocityTracker == null)
                            mVelocityTracker = VelocityTracker.obtain();
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
                try {
                    if ((mPrivateFlags & PFLAG_IMAGE_BEING_DRAGGED) == 0) {
                        break;
                    }

                    final int width = getWidth() - getPaddingLeft() - getPaddingRight();
                    final int height = getHeight() - getPaddingTop() - getPaddingBottom();

                    resolveImageBounds(mImageMatrix);

                    float scaleX = getImageScaleX();
                    float scaleY = getImageScaleY();
                    // If the current scale of the image is larger than the maximum scale
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

                    // No scaling is needed below
                    if (mVelocityTracker == null) {
                        break;
                    }
                    mVelocityTracker.computeCurrentVelocity(1000, mMaximumFlingVelocity);
                    final float vx = mVelocityTracker.getXVelocity(mActivePointerId);
                    final float vy = mVelocityTracker.getYVelocity(mActivePointerId);
                    // If one of the velocities is not less than our minimum fling velocity,
                    // treat it as fling as user raises up his/her last finger that is
                    // touching the screen.
                    if ((Math.abs(vx) >= mMinimumFlingVelocity
                            || Math.abs(vy) >= mMinimumFlingVelocity)) {
                        final float imgWidth = mImageBounds.width();
                        final float imgHeight = mImageBounds.height();
                        // Only when the width or height of the image is greater than
                        // the width or height of the view can this fling be valid
                        // to translate the image through animator to show its other area
                        // to the user.
                        if (imgWidth > width || imgHeight > height) {
                            float dx = vx * RATIO_FLING_OFFSET_TO_VELOCITY;
                            float dy = vy * RATIO_FLING_OFFSET_TO_VELOCITY;
                            /*
                             * Adjust dx and dy to make the image translated under our control
                             * (finally let it fit current view).
                             *
                             * @see #computeImageTranslationByOnScaled(Matrix)
                             */
                            mOverTranslationX = mOverTranslationY = 0f;
                            if (imgWidth > width) {
                                if (mImageBounds.left + dx >= 0f) {
                                    if (mImageBounds.left < 0f)
                                        mOverTranslationX = mImageOverTranslation;
                                    dx = -mImageBounds.left + mOverTranslationX;
                                } else if (mImageBounds.right + dx <= width) {
                                    if (mImageBounds.right > width)
                                        mOverTranslationX = -mImageOverTranslation;
                                    dx = width - mImageBounds.right + mOverTranslationX;
                                }
                            } else {
                                dx = (width + imgWidth) / 2f - mImageBounds.right;
                            }
                            if (imgHeight > height) {
                                if (mImageBounds.top + dy >= 0f) {
                                    if (mImageBounds.top < 0f)
                                        mOverTranslationY = mImageOverTranslation;
                                    dy = -mImageBounds.top + mOverTranslationY;
                                } else if (mImageBounds.bottom + dy <= height) {
                                    if (mImageBounds.bottom > height)
                                        mOverTranslationY = -mImageOverTranslation;
                                    dy = height - mImageBounds.bottom + mOverTranslationY;
                                }
                            } else {
                                dy = (height + imgHeight) / 2f - mImageBounds.bottom;
                            }
                            if (dx == 0f && dy == 0f) {
                                break;
                            }
                            PointF trans = getImageTranslation();
                            animateTranslatingImage(trans, new PointF(trans.x + dx, trans.y + dy));

                            if (mOverTranslationX == 0 && mOverTranslationY == 0) {
                                break;
                            }
                            postDelayed(mImageSpringBackRunnable, DURATION_ANIMATE_IMAGE);
                            break;
                        }
                    }
                    // Not else!
                    // Here regard it as a normal scroll
                    PointF trans = getImageTranslation();
                    PointF finalTrans = computeImageTranslationByOnScaled(mImageMatrix);
                    finalTrans.offset(trans.x, trans.y);
                    animateTranslatingImage(trans, finalTrans);
                    break;
                } finally {
                    clearTouch();
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

    private void clearTouch() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
        resetTouch();
    }

    private void resetTouch() {
        mActivePointerId = ViewDragHelper.INVALID_POINTER;
        mPrivateFlags &= ~(PFLAG_IMAGE_BEING_DRAGGED
                | PFLAG_HAS_PERFORMED_LONG_CLICK | PFLAG_LONG_CLICK_CONSUMED);
        if (mVelocityTracker != null) {
            mVelocityTracker.clear();
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
     * Smoothly scale the image through animator.
     * <p>
     * <strong>NOTE:</strong> This can be simultaneously used with
     * {@link #animateTranslatingImage(PointF, PointF)},
     * but you'd better call it before the latter is invoked.
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
        if (getDrawable() == null || fromX == toX && fromY == toY) {
            return;
        }

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
            mScaleImageAnimator.addUpdateListener(mImageAnimatorsUpdateListener);
        } else {
            if (mScaleImageAnimator.isRunning()) {
                mScaleImageAnimator.cancel();
            }
            mScaleImageAnimator.setObjectValues(from, to);
        }
        mScaleImageAnimator.start();
    }

    /**
     * Smoothly translate the image through animator.
     * <p>
     * <strong>NOTE:</strong> This can be simultaneously used with
     * {@link #animateScalingImage(float, float, float, float, float, float)},
     * but you'd better call it immediately after the latter was invoked.
     *
     * @param from the current translation value of the matrix of the image
     * @param to   the final translation value of the matrix of the image
     */
    public void animateTranslatingImage(PointF from, PointF to) {
        if (getDrawable() == null || from.x == to.x && from.y == to.y) {
            return;
        }

        ensureImageMatrix();
        mLastAnimatedTranslationX = from.x;
        mLastAnimatedTranslationY = from.y;

        if (mTranslateImageAnimator == null) {
            mTranslateImageAnimator = ValueAnimator.ofObject(new PointFEvaluator(), from, to);
            mTranslateImageAnimator.setDuration(DURATION_ANIMATE_IMAGE);
            mTranslateImageAnimator.addUpdateListener(mImageAnimatorsUpdateListener);
        } else {
            if (mTranslateImageAnimator.isRunning()) {
                mTranslateImageAnimator.cancel();
            }
            mTranslateImageAnimator.setObjectValues(from, to);
        }
        mTranslateImageAnimator.start();
    }

    private final ValueAnimator.AnimatorUpdateListener mImageAnimatorsUpdateListener
            = new ValueAnimator.AnimatorUpdateListener() {
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
    };

    /**
     * Cancel all running animators and the pending animation that will bounce this image back
     * after it is over-translated.
     */
    private void cancelAnimations() {
        // May be that we have scheduled a Runnable to rebound this image back after it is
        // over-translated, which yet hasn't started and should be canceled.
        removeCallbacks(mImageSpringBackRunnable);

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
