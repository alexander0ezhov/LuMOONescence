package com.limelight.binding.input.virtual_controller.universal;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Hands a shared layout to the app the user shares it with.
 *
 * The project is built on the framework alone, so this is the small part of a file provider
 * that is actually needed: one readable file out of the app's own cache. Nothing outside the
 * cache directory can be reached through it, whatever address is asked for.
 */
public class UniversalLayoutShareProvider extends ContentProvider {
    /**
     * The authority this provider is published under, which the manifest builds from the
     * application id so debug and release builds do not collide.
     */
    public static String authority(android.content.Context context) {
        return context.getPackageName() + ".fileprovider";
    }

    private static final String DIRECTORY = "shared-layouts";

    private static final String MIME_TYPE = "application/json";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return MIME_TYPE;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("layouts are only shared for reading");
        }

        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        File file = fileFor(uri);

        // The name and the size are what a receiving app shows and checks before copying
        String[] columns = projection != null ? projection
                : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};

        MatrixCursor cursor = new MatrixCursor(columns);
        Object[] row = new Object[columns.length];

        for (int i = 0; i < columns.length; i++) {
            row[i] = OpenableColumns.SIZE.equals(columns[i])
                    ? (Object) file.length()
                    : (Object) file.getName();
        }

        cursor.addRow(row);
        return cursor;
    }

    /**
     * The file an address stands for, which must be one of ours.
     */
    private File fileFor(Uri uri) {
        String name = uri.getLastPathSegment();

        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("no file in " + uri);
        }

        File directory = new File(getContext().getCacheDir(), DIRECTORY);

        // Only a plain name inside the directory is allowed, so an address cannot be used to
        // walk out of it
        if (name.contains("/") || name.contains("..")) {
            throw new IllegalArgumentException("not a shared layout: " + name);
        }

        return new File(directory, name);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("shared layouts are read only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("shared layouts are read only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("shared layouts are read only");
    }
}
