package com.limelight.binding.input.virtual_controller.universal;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.utils.UiHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Lets the user pick the artwork of a universal layout from the images on the device.
 *
 * The picked image is copied into the app's storage, so the layout keeps working even
 * if the original image is moved or its permission is revoked.
 */
public class UniversalLayoutImagePickerActivity extends Activity {
    public static final String EXTRA_LAYOUT_ID = "layoutId";

    private static final long MAX_COPY_BYTES = 32L * 1024 * 1024;

    private UniversalLayoutStore layoutStore;
    private UniversalLayoutDefinition layout;

    private GridView imageGrid;
    private ProgressBar progressBar;
    private TextView emptyText;
    private ImageAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String layoutId = getIntent().getStringExtra(EXTRA_LAYOUT_ID);
        if (layoutId == null) {
            finish();
            return;
        }

        layoutStore = UniversalLayoutStore.getInstance(this);
        layout = layoutStore.getLayout(layoutId);
        if (layout == null) {
            finish();
            return;
        }

        setContentView(R.layout.activity_universal_layout_image_picker);

        // Compensates drawing under the status bar, so the toolbar is not positioned
        // under it. Every normal activity in this app does this.
        UiHelper.notifyNewRootView(this);

        imageGrid = findViewById(R.id.imagePickerGrid);
        progressBar = findViewById(R.id.imagePickerProgress);
        emptyText = findViewById(R.id.imagePickerEmptyText);

        adapter = new ImageAdapter();
        imageGrid.setAdapter(adapter);

        // Keep the last row of thumbnails above the navigation bar
        UiHelper.applyStatusBarPadding(imageGrid);
        imageGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                applyImage(adapter.getItem(position));
            }
        });

        findViewById(R.id.imagePickerBackButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        findViewById(R.id.imagePickerDefaultButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                layout.setImagePath(UniversalLayoutDefinition.DEFAULT_IMAGE_PATH);
                layoutStore.updateLayout(layout);
                finish();
            }
        });

        loadImages();
    }

    private void loadImages() {
        // The list is filled in one go when the scan finishes, so keep it hidden until
        // then instead of flashing an empty grid.
        imageGrid.setVisibility(View.INVISIBLE);

        new AsyncTask<Void, Void, List<String>>() {
            @Override
            protected List<String> doInBackground(Void... params) {
                return queryImages();
            }

            @Override
            protected void onPostExecute(List<String> paths) {
                if (isFinishing()) {
                    return;
                }

                progressBar.setVisibility(View.GONE);
                imageGrid.setVisibility(View.VISIBLE);

                adapter.setImages(paths);
                emptyText.setVisibility(paths.isEmpty() ? View.VISIBLE : View.GONE);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Reads the images of the device into memory and closes the cursor immediately,
     * because a MediaStore cursor is only valid while it stays open.
     */
    private List<String> queryImages() {
        List<String> paths = new ArrayList<>();

        String[] projection = new String[]{MediaStore.Images.Media.DATA};
        String selection = MediaStore.Images.Media.SIZE + " > 0";
        String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";

        try (Cursor cursor = getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, null, sortOrder)) {

            if (cursor == null) {
                return paths;
            }

            int dataColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATA);

            while (cursor.moveToNext()) {
                // MediaStore can hold entries whose file is gone, so only keep real files
                if (dataColumn >= 0) {
                    String path = cursor.getString(dataColumn);
                    if (path != null && new File(path).isFile()) {
                        paths.add(path);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return paths;
    }

    private void applyImage(String sourcePath) {
        try {
            String destination = copyIntoAppStorage(sourcePath);
            if (destination == null) {
                Toast.makeText(this, R.string.msg_image_picker_failed, Toast.LENGTH_SHORT).show();
                return;
            }

            deletePreviousImage();

            layout.setImagePath(destination);
            layoutStore.updateLayout(layout);
            finish();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.msg_image_picker_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Copies the chosen image next to the app's own files and returns its path.
     */
    private String copyIntoAppStorage(String sourcePath) throws IOException {
        File source = new File(sourcePath);
        if (!source.isFile() || source.length() > MAX_COPY_BYTES) {
            return null;
        }

        File directory = new File(getFilesDir(), "layout_images");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            return null;
        }

        File destination = new File(directory, layout.getId() + "_" + System.currentTimeMillis() + ".img");

        try (InputStream input = new java.io.FileInputStream(source);
             OutputStream output = new FileOutputStream(destination)) {

            byte[] buffer = new byte[32 * 1024];
            long copied = 0;
            int read;

            while ((read = input.read(buffer)) > 0) {
                copied += read;
                if (copied > MAX_COPY_BYTES) {
                    // The file changed size while copying
                    output.close();
                    destination.delete();
                    return null;
                }

                output.write(buffer, 0, read);
            }
        }

        return destination.getAbsolutePath();
    }

    private void deletePreviousImage() {
        if (!layout.hasCustomImage()) {
            return;
        }

        File previous = new File(layout.getImagePath());
        if (previous.isFile()) {
            previous.delete();
        }
    }

    /**
     * Grid adapter that decodes the device's images in the background.
     */
    private class ImageAdapter extends UniversalImageAdapter {
        private final List<String> images = new ArrayList<>();

        ImageAdapter() {
            super(0,
                    (int) getResources().getDimension(R.dimen.layout_picker_item_image_width),
                    (int) getResources().getDimension(R.dimen.layout_picker_item_image_height));
        }

        void setImages(List<String> newImages) {
            images.clear();
            images.addAll(newImages);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return images.size();
        }

        @Override
        public String getItem(int position) {
            return images.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public String getImagePath(int position) {
            return getItem(position);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(UniversalLayoutImagePickerActivity.this)
                        .inflate(R.layout.universal_layout_image_grid_item, parent, false);
            }

            bindImage(view.findViewById(R.id.imagePickerItemImage), position);

            return view;
        }
    }
}
