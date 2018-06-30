package com.liuzhenlin.gallery_viewer_sample;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.liuzhenlin.gallery_viewer.GalleryViewPager;

import java.util.ArrayList;
import java.util.List;

/**
 * @author 刘振林
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener, View.OnLongClickListener {
    private GalleryViewPager mGalleryViewPager;
    private FrameLayout mDeleteFrame;

    private final List<ImageView> mImages = new ArrayList<>(PICTURE_COUNT);
    private static final int PICTURE_COUNT = 7;

    private static final int TAG_IMAGE_INITIAL_POSITION = 10 << 24;
    private static final int TAG_IMAGE_ADAPTER_POSITION = 20 << 24;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        for (int i = 0; i < PICTURE_COUNT; i++) {
            ImageView image = (ImageView) View.inflate(this,
                    R.layout.item_gallery_view_pager, null);
            image.setOnLongClickListener(this);
            image.setOnClickListener(this);
            image.setTag(TAG_IMAGE_INITIAL_POSITION, i);
            mImages.add(image);
        }
        mGalleryViewPager = findViewById(R.id.galley_view_pager);
        GalleryPagerAdapter adapter = new GalleryPagerAdapter();
        mGalleryViewPager.setAdapter(adapter);
        mGalleryViewPager.setItemCallback(adapter);
        mGalleryViewPager.setPageMargin((int) (20f * getResources().getDisplayMetrics().density + 0.5f));

        mDeleteFrame = findViewById(R.id.frame_image_bt_delete);
        mDeleteFrame.setOnClickListener(this);
        findViewById(R.id.image_bt_delete).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.image_picture:
                if (mDeleteFrame.getVisibility() == View.VISIBLE) {
                    mDeleteFrame.setVisibility(View.INVISIBLE);
                    break;
                }
                finish();
                break;

            case R.id.frame_image_bt_delete:
            case R.id.image_bt_delete:
                final int pictureCount = mImages.size();
                if (pictureCount > 0) {
                    mImages.remove(mGalleryViewPager.getCurrentItem());
                    // noinspection all (adapter is nonnull)
                    mGalleryViewPager.getAdapter().notifyDataSetChanged();
                    if (pictureCount == 1) {
                        finish();
                    }
                }
                break;
        }
    }

    @Override
    public boolean onLongClick(View v) {
        switch (v.getId()) {
            case R.id.image_picture:
                if (mDeleteFrame.getVisibility() != View.VISIBLE) {
                    mDeleteFrame.setVisibility(View.VISIBLE);
                    return true;
                }
                break;
        }
        return false;
    }

    private class GalleryPagerAdapter extends PagerAdapter implements GalleryViewPager.ItemCallback {

        @Override
        public int getCount() {
            return mImages.size();
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            ImageView image = mImages.get(position);
            image.setImageDrawable(ResourcesCompat.getDrawable(getResources(), getResources()
                    .getIdentifier("picture" + image.getTag(TAG_IMAGE_INITIAL_POSITION),
                            "drawable", getPackageName()), getTheme()));
            image.setTag(TAG_IMAGE_ADAPTER_POSITION, position);

            if (image.getParent() != null) {
                container.removeView(image);
            }
            container.addView(image);
            return image;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            ImageView image = (ImageView) object;
            image.setImageDrawable(null);
            container.removeView(image);
        }

        @Override
        public int getItemPosition(@NonNull Object object) {
            View view = (View) object;
            ViewPager parent = (ViewPager) view.getParent();
            if (parent == null ||
                    ((int) view.getTag(TAG_IMAGE_ADAPTER_POSITION)) == parent.getCurrentItem()) {
                return POSITION_NONE;
            }
            return POSITION_UNCHANGED;
        }

        @Override
        public Object getItemAt(int position) {
            if (position >= 0 && position < mImages.size()) {
                return mImages.get(position);
            }
            return null;
        }
    }
}
