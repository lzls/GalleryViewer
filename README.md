# GalleryViewer [![](https://jitpack.io/v/freeze-frames/GalleryViewer.svg)](https://jitpack.io/#freeze-frames/GalleryViewer)
A library provides a GalleryViewPager and a GestureImageView to enable you to preview pictures with
scale, drag and fling gestures.

<div align="center">
    <img src="https://github.com/ApksHolder/GalleryViewer/blob/master/preview.gif" width="300">
</div>


## GalleryViewPager
A ViewPager can prevent the sliding conflicts when you tend to drag image rather than the ViewPager itself.

**Usages:**
Similar to ViewPager. But it's necessary for you to set an ItemCallback for it.
```Java
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
```
Here is a sample:
```Java
private final List<ImageView> mImages = new ArrayList<>(PICTURE_COUNT);
private static final int PICTURE_COUNT = 7;

private static final int TAG_IMAGE_INITIAL_POSITION = 10 << 24;

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
    mGalleryViewPager.setPageMargin((int) (25f * getResources().getDisplayMetrics().density + 0.5f));
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
        if (image.getParent() == null) {
            image.setImageDrawable(ResourcesCompat.getDrawable(getResources(), getResources()
                    .getIdentifier("picture" + image.getTag(TAG_IMAGE_INITIAL_POSITION),
                            "drawable", getPackageName()), getTheme()));
            container.addView(image);
        }
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
        return POSITION_NONE;
    }

    @Override
    public Object getItemAt(int position) {
        if (position >= 0 && position < mImages.size()) {
            return mImages.get(position);
        }
        return null;
    }
}
```

**`Note that there does not exist any cache strategy in it, so it's necessary for you
to apply some caches in its adapter to cache the images that need to be displayed.`**


## GestureImageView
An ImageView can scale and/or translate its image while you are touching it with zoom in and out
and/or drag and drop gestures.

```Java
public class GestureImageView extends AppCompatImageView
```

**`For more  details, please download source code to see.`**


## Pull Requests
I will gladly accept pull requests for bug fixes and feature enhancements but please do them
in the developers branch.


## License
Copyright 2018 刘振林

Licensed under the Apache License, Version 2.0 (the "License"); <br>
you may not use this file except in compliance with the License. You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License
is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
or implied. See the License for the specific language governing permissions and limitations
under the license
