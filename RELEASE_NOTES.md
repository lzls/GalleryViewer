# Release Notes

### 2.1 (2021-11-04)
* Library
    * Adjust the threshold where a picture will be deemed to be a long picture.
    * Use `OverScroller` instead of `Animator` for better image fling (fast scrolling) experience.
    * Disallow an enlarged image to scroll in a case where the user prefers it to be not moved
      in an undesired direction as the user is fast scrolling the screen, which may eventually
      trigger a fling gesture.
    * Add overscroll effect for each `GestureImageView` page in `GalleryViewPager`.

### 2.0.1 (2021-08-15)
* Library
    * Fix horizontal sliding conflicts between `GestureImageView` and `GalleryViewPager`,
      esp. on high density mobile phone models.

### 2.0 (2021-03-02)
* No notes provided.

### 1.1.1 (2019-11-30)
* Library
    * Display & zoom optimizations for strip pictures (h >(>) w).

### 1.1 (2018-12-15)
* No notes provided.

### 1.0.1 (2018-11-23)
* Library
    * Bug fixes.

### 1.0 (2018-06-30)
* No notes provided.