package com.ayush.quietlib.rfm;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import com.ayush.quietlib.R;

import java.io.IOException;
import java.io.InputStream;

public class SubPage extends AppCompatActivity {

    ImageView img;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sub_page);

        Bundle b = getIntent().getExtras();
        String id = b.getString("id");

        ImageView img = findViewById(R.id.subPageImg);

        try {
            String filename = "subpages/" + id + ".png";
            InputStream is = getAssets().open(filename);
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            img.setImageBitmap(bitmap);
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "This page hasn't been cached yet.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

}