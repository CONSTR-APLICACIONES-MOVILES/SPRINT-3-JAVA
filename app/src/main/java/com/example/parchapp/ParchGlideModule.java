package com.example.parchapp;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.load.engine.cache.LruResourceCache;
import com.bumptech.glide.load.engine.cache.MemorySizeCalculator;
import com.bumptech.glide.module.AppGlideModule;

/* Caching: the two caches of glide, sized for what ParchApp shows (small round profile photos).
 * A photo is looked for in this order: active resources (the ones on screen now), then the memory cache (an LRU of
 * decoded bitmaps), then the disk cache (an LRU of files), and only then the network or the file on the phone. */
@GlideModule
public final class ParchGlideModule extends AppGlideModule {
    // the app only shows small round photos, 20 MB of files is hundreds of them; glide's default is 250 MB
    static final long DISK_CACHE_BYTES = 20L * 1024 * 1024;
    // memory for the bitmaps of two full screens; the calculator also checks the RAM class of the phone, so a
    // low-memory phone gets less
    static final float MEMORY_CACHE_SCREENS = 2f;

    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        MemorySizeCalculator sizes = new MemorySizeCalculator.Builder(context)
                .setMemoryCacheScreens(MEMORY_CACHE_SCREENS)
                .build();
        builder.setMemoryCache(new LruResourceCache(sizes.getMemoryCacheSize()));
        // the internal cache folder: private to the app, and android may clear it when the phone needs space
        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, "parch_images", DISK_CACHE_BYTES));
    }

    // no old glide modules declared in the manifest, so glide does not parse it at start
    @Override
    public boolean isManifestParsingEnabled() {
        return false;
    }
}
