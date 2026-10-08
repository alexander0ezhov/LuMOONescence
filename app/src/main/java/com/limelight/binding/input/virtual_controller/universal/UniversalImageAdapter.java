package com.limelight.binding.input.virtual_controller.universal;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.widget.BaseAdapter;
import android.widget.ImageView;

/**
 * Grid adapter that shows a fixed-size thumbnail of an image file.
 *
 * Both the layout list and the image picker show artwork in a grid, and both need the
 * decoding off the main thread for the same reason: a photo on the device can be far
 * larger than the thumbnail that is displayed.
 */
abstract class UniversalImageAdapter extends BaseAdapter {
    private final int placeholderResource;
    private final int decodeWidth;
    private final int decodeHeight;

    /**
     * @param placeholderResource drawable shown while the image is decoded, or 0 to
     *                            leave the view blank
     * @param decodeWidth         width the image is decoded at
     * @param decodeHeight        height the image is decoded at
     */
    UniversalImageAdapter(int placeholderResource, int decodeWidth, int decodeHeight) {
        this.placeholderResource = placeholderResource;
        this.decodeWidth = decodeWidth;
        this.decodeHeight = decodeHeight;
    }

    /**
     * Returns the image file of the item, or an empty string when this item has no
     * artwork of its own and the placeholder should stay.
     */
    abstract String getImagePath(int position);

    /**
     * Applies the artwork of an item to its grid item view.
     */
    void bindImage(ImageView imageView, int position) {
        String path = getImagePath(position);

        // Tagging the view is what keeps a recycled view from showing the image that was
        // decoded for the item it used to display.
        imageView.setTag(path);

        if (placeholderResource != 0) {
            imageView.setImageResource(placeholderResource);
        }
        else {
            imageView.setImageDrawable(null);
        }

        if (path == null || path.isEmpty()) {
            return;
        }

        loadInto(imageView, path);
    }

    private void loadInto(final ImageView imageView, final String path) {
        new AsyncTask<Void, Void, Bitmap>() {
            @Override
            protected Bitmap doInBackground(Void... params) {
                return UniversalLayoutImageLoader.load(path, decodeWidth, decodeHeight);
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if (bitmap != null && path.equals(imageView.getTag())) {
                    imageView.setImageBitmap(bitmap);
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
}
