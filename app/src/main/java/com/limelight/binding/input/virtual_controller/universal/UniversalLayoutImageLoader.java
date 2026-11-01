package com.limelight.binding.input.virtual_controller.universal;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Loads and caches the artwork that identifies a universal layout.
 *
 * Images come from a user-chosen file and can be large, so they are always decoded at
 * the requested size. The cache is bounded by the artwork of the user's layouts, which
 * is small, but it is still capped to keep a long browsing session from growing.
 */
public class UniversalLayoutImageLoader {
    private static final int MAX_CACHED_IMAGES = 48;

    private static final Map<String, Bitmap> cache = new HashMap<>();

    private UniversalLayoutImageLoader() {
    }

    private static String makeCacheKey(String path, int width, int height) {
        return path + "@" + width + "x" + height;
    }

    /**
     * Returns the decoded image, or null if it is missing or not a readable image.
     * This must not be called on the main thread.
     */
    public static Bitmap load(String path, int width, int height) {
        if (path == null || path.isEmpty() || width <= 0 || height <= 0) {
            return null;
        }

        String key = makeCacheKey(path, width, height);

        synchronized (cache) {
            Bitmap cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
        }

        Bitmap bitmap = decode(path, width, height);
        if (bitmap == null) {
            return null;
        }

        synchronized (cache) {
            if (cache.size() >= MAX_CACHED_IMAGES) {
                // Drop arbitrary entries rather than tracking usage: the worst case is a
                // re-decode, and the images are few.
                Iterator<String> iterator = cache.keySet().iterator();
                while (iterator.hasNext() && cache.size() >= MAX_CACHED_IMAGES / 2) {
                    iterator.next();
                    iterator.remove();
                }
            }

            cache.put(key, bitmap);
        }

        return bitmap;
    }

    private static Bitmap decode(String path, int width, int height) {
        File file = new File(path);
        if (!file.isFile()) {
            return null;
        }

        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, width, height);
            options.inPreferredConfig = Bitmap.Config.RGB_565;

            return BitmapFactory.decodeFile(path, options);
        } catch (OutOfMemoryError e) {
            // A huge image can still fail even after downsampling
            return null;
        }
    }

    private static int calculateSampleSize(int sourceWidth, int sourceHeight, int targetWidth, int targetHeight) {
        int sampleSize = 1;

        while (sourceWidth / (sampleSize * 2) >= targetWidth &&
                sourceHeight / (sampleSize * 2) >= targetHeight) {
            sampleSize *= 2;
        }

        return sampleSize;
    }
}
