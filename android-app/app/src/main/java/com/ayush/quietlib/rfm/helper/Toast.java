package com.ayush.quietlib.rfm.helper;

import static android.widget.Toast.makeText;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.StringRes;

/**
 * vlad805 (c) 2020
 */
public class Toast {

	public static Toast create(Context context) {
		return new Toast(context);
	}

	private final android.widget.Toast toast;

	@SuppressLint("ShowToast")
	private Toast(Context context) {
		toast = makeText(context, "", android.widget.Toast.LENGTH_LONG);
	}

	public Toast text(String text) {
		toast.setText(text);
		return this;
	}

	public Toast text(String text, Object... args) {
		toast.setText(String.format(text, args));
		return this;
	}

	public Toast text(@StringRes int text) {
		toast.setText(text);
		return this;
	}

	public Toast show() {
		toast.show();
		return this;
	}

	public Toast hide() {
		toast.cancel();
		return this;
	}
}
