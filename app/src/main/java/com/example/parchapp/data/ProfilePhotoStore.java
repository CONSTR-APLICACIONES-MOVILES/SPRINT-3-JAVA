package com.example.parchapp.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.system.ErrnoException;
import android.system.Os;

import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Private on-device photo files and the resize/compression policy shared by capture and sync. */
public final class ProfilePhotoStore {
    public static final int MAX_PHOTO_BYTES = 130_000;
    private static final int MAX_DIMENSION = 320;
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor();

    private ProfilePhotoStore() {
    }

    public static void execute(Runnable work) {
        IO_EXECUTOR.execute(work);
    }

    public static File createCaptureFile(Context context) throws IOException {
        File directory = photoDirectory(context);
        File capture = File.createTempFile("capture_", ".jpg", directory);
        if (!capture.delete()) {
            throw new IOException("Could not prepare a temporary camera file.");
        }
        return capture;
    }

    public static File cachedPhoto(Context context, String userId) throws IOException {
        return new File(photoDirectory(context), safeUserId(userId) + ".img");
    }

    public static File stagePhoto(Context context, String userId, byte[] jpeg) throws IOException {
        if (jpeg == null || jpeg.length == 0 || jpeg.length > MAX_PHOTO_BYTES) {
            throw new IOException("The compressed profile photo must be under 130 KB.");
        }
        File staged = File.createTempFile("photo_" + safeUserId(userId) + "_", ".pending",
                photoDirectory(context));
        try (FileOutputStream output = new FileOutputStream(staged)) {
            output.write(jpeg);
            output.flush();
            output.getFD().sync();
        } catch (IOException error) {
            deleteQuietly(staged);
            throw error;
        }
        return staged;
    }

    /** Atomically replaces the UID-specific cached photo after Backend acknowledges the upload. */
    public static void commitPhoto(Context context, String userId, File staged) throws IOException {
        File target = cachedPhoto(context, userId);
        try {
            Os.rename(staged.getAbsolutePath(), target.getAbsolutePath());
        } catch (ErrnoException error) {
            throw new IOException("Could not save the profile photo on this device.", error);
        }
    }

    public static byte[] compressForProfile(File sourceFile) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(sourceFile.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("The camera did not create a valid image.");
        }

        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sample > 1280) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap decoded = BitmapFactory.decodeFile(sourceFile.getAbsolutePath(), options);
        if (decoded == null) {
            throw new IOException("Could not read the captured photo.");
        }

        Bitmap oriented = applyExifOrientation(sourceFile, decoded);
        try {
            for (int dimension = MAX_DIMENSION; dimension >= 128; dimension = dimension * 4 / 5) {
                Bitmap resized = resize(oriented, dimension);
                try {
                    for (int quality = 65; quality >= 35; quality -= 10) {
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        if (resized.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                            byte[] jpeg = output.toByteArray();
                            if (jpeg.length <= MAX_PHOTO_BYTES) {
                                return jpeg;
                            }
                        }
                    }
                } finally {
                    if (resized != oriented) {
                        resized.recycle();
                    }
                }
            }
        } finally {
            oriented.recycle();
        }
        throw new IOException("Could not compress the photo below 130 KB.");
    }

    public static Bitmap loadCachedBitmap(Context context, String userId) {
        try {
            File cached = cachedPhoto(context, userId);
            return cached.isFile() ? BitmapFactory.decodeFile(cached.getAbsolutePath()) : null;
        } catch (IOException | IllegalArgumentException ignored) {
            return null;
        }
    }

    public static Bitmap decodeImage(byte[] image) {
        return BitmapFactory.decodeByteArray(image, 0, image.length);
    }

    public static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            // A failed deletion leaves only a private temporary file, never the active avatar.
            file.delete();
        }
    }

    private static File photoDirectory(Context context) throws IOException {
        File directory = new File(context.getFilesDir(), "profile_photos");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create private profile-photo storage.");
        }
        return directory;
    }

    private static String safeUserId(String userId) throws IOException {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IOException("A signed-in user is required to store a profile photo.");
        }
        return userId.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static Bitmap resize(Bitmap source, int maxDimension) {
        float scale = Math.min(1f, maxDimension / (float) Math.max(source.getWidth(), source.getHeight()));
        if (scale >= 1f) {
            return source;
        }
        int width = Math.max(1, Math.round(source.getWidth() * scale));
        int height = Math.max(1, Math.round(source.getHeight() * scale));
        return Bitmap.createScaledBitmap(source, width, height, true);
    }

    private static Bitmap applyExifOrientation(File file, Bitmap source) {
        try {
            int orientation = new ExifInterface(file.getAbsolutePath())
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    matrix.setScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.setRotate(180);
                    break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    matrix.setRotate(180);
                    matrix.postScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_TRANSPOSE:
                    matrix.setRotate(90);
                    matrix.postScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.setRotate(90);
                    break;
                case ExifInterface.ORIENTATION_TRANSVERSE:
                    matrix.setRotate(-90);
                    matrix.postScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.setRotate(-90);
                    break;
                default:
                    return source;
            }
            Bitmap corrected = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
            if (corrected != source) {
                source.recycle();
            }
            return corrected;
        } catch (IOException | RuntimeException ignored) {
            return source;
        }
    }
}
