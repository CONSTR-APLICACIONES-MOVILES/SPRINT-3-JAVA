package com.example.parchapp.util;

import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.TextView;

// a button with drawableStart puts the icon on the left edge and the text in the middle
// this keeps the icon next to the text and puts both in the middle of the button
public final class IconButtons {

    private IconButtons() {
    }

    public static void centerIconWithText(TextView button) {
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                applyPadding(button));
        button.post(() -> applyPadding(button));
    }

// the same space on both sides of the icon and the text
    private static void applyPadding(TextView button) {
        Drawable icon = button.getCompoundDrawablesRelative()[0];
        int iconWidth = icon == null ? 0 : icon.getIntrinsicWidth() + button.getCompoundDrawablePadding();
        int textWidth = (int) Math.ceil(button.getPaint().measureText(button.getText().toString()));
        int side = Math.max(0, (button.getWidth() - iconWidth - textWidth) / 2);
// we only change it when it is different, so the layout does not run again and again
        if (button.getPaddingStart() != side || button.getPaddingEnd() != side) {
            button.setPaddingRelative(side, button.getPaddingTop(), side, button.getPaddingBottom());
        }
    }
}
