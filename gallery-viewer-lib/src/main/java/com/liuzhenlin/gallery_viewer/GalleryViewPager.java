package com.liuzhenlin.gallery_viewer;

import android.content.Context;
import android.graphics.RectF;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.ViewParent;

/**
 * @author <a href="mailto:2233788867@qq.com">刘振林</a>
 */
public class GalleryViewPager extends ViewPager {
    private static final String TAG = "GalleryViewPager";

    protected final int mTouchSlop;

    private int mActivePointerId = ViewDragHelper.INVALID_POINTER;
    private float mDownX;
    private float mDownY;

    private VelocityTracker mVelocityTracker;

    /**
     * The minimum velocity to fling this view when the image in the current page is magnified
     *
     * @see GestureImageView
     */
    private final int mMinimumFlingVelocityOnCurrImageMagnified; // 800 dp/s

    /** Position of the last selected page */
    private int mLastSelectedPageIndex;

    private final OnPageChangeListener mInternalOnPageChangeListener = new SimpleOnPageChangeListener() {
        @Override
        public void onPageSelected(int position) {
            if (mLastSelectedPageIndex != position && mItemCallback != null) {
                Object lastItem = mItemCallback.getItemAt(mLastSelectedPageIndex);
                if (lastItem instanceof GestureImageView) {
                    ((GestureImageView) lastItem).reinitializeImage();
                }
            }
            mLastSelectedPageIndex = position;
        }
    };

    public GalleryViewPager(@NonNull Context context) {
        this(context, null);
    }

    public GalleryViewPager(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        ViewConfiguration vc = ViewConfiguration.get(context);
        mTouchSlop = vc.getScaledTouchSlop();
        mMinimumFlingVelocityOnCurrImageMagnified =
                (int) (vc.getScaledMaximumFlingVelocity() / 10f + 0.5f);
        addOnPageChangeListener(mInternalOnPageChangeListener);
    }

    @Override
    public void clearOnPageChangeListeners() {
        super.clearOnPageChangeListeners();
        addOnPageChangeListener(mInternalOnPageChangeListener);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final int actionMasked = ev.getAction() & MotionEvent.ACTION_MASK;

        if (actionMasked == MotionEvent.ACTION_DOWN) {
            initOrClearVelocityTracker();
        }
        if (mVelocityTracker != null) {
            mVelocityTracker.addMovement(ev);
        }

        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                onPointerDown(ev);
                break;
            case MotionEvent.ACTION_MOVE:
                final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    Log.e(TAG, "Error processing scroll; pointer index for id "
                            + mActivePointerId + " not found. Did any MotionEvents get skipped?");
                    return false;
                }

                if (mItemCallback == null) break;
                Object item = mItemCallback.getItemAt(getCurrentItem());
                if (item instanceof GestureImageView) {
                    if (ev.getPointerCount() != 1) {
                        return false;
                    }

                    final float absDX = Math.abs(ev.getX() - mDownX);
                    boolean intercept = absDX > mTouchSlop && absDX > Math.abs(ev.getY() - mDownY);
                    if (!intercept) return false;

                    mVelocityTracker.computeCurrentVelocity(1000);
                    final float vx = mVelocityTracker.getXVelocity(mActivePointerId);

                    GestureImageView image = (GestureImageView) item;
                    RectF imgBounds = image.getImageBounds();
                    if (imgBounds == null) return true;
                    final int imgAvailableWidth =
                            image.getWidth() - image.getPaddingLeft() - image.getPaddingRight();
                    if (imgBounds.width() > imgAvailableWidth) {
                        final boolean tryScrollPageRight = imgBounds.left >= 0f
                                && vx >= mMinimumFlingVelocityOnCurrImageMagnified;
                        final boolean tryScrollPageLeft = imgBounds.right <= imgAvailableWidth
                                && vx <= -mMinimumFlingVelocityOnCurrImageMagnified;
                        intercept = tryScrollPageLeft || tryScrollPageRight;
                    }

                    if (intercept) {
                        mActivePointerId = ViewDragHelper.INVALID_POINTER;
                        recycleVelocityTracker();

                        ViewParent parent = getParent();
                        if (parent != null) {
                            parent.requestDisallowInterceptTouchEvent(true);
                        }
                        return true;
                    }
                    return false;
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mActivePointerId = ViewDragHelper.INVALID_POINTER;
                recycleVelocityTracker();
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    private void onPointerDown(MotionEvent e) {
        final int actionIndex = e.getActionIndex();
        mActivePointerId = e.getPointerId(actionIndex);
        mDownX = e.getX(actionIndex);
        mDownY = e.getY(actionIndex);
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
        }
    }

    private void initOrClearVelocityTracker() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        } else {
            mVelocityTracker.clear();
        }
    }

    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    private ItemCallback mItemCallback;

    public void setItemCallback(@Nullable ItemCallback callback) {
        mItemCallback = callback;
    }

    public interface ItemCallback {
        /**
         * @param position the <strong>adapter position</strong> of the item that you want to get
         * @return the item at the specified position
         */
        Object getItemAt(int position);
    }
}
