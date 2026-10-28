package com.example.parchapp.util;

/*Result of an asynchronous operation. Always delivered on the main thread so controllers can touch views directly.
 */
public interface Callback<T> {
    void onSuccess(T result);

    void onError(Exception error);
}
