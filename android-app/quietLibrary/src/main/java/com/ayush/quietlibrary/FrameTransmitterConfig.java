package com.ayush.quietlibrary;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

public class FrameTransmitterConfig {
    private native long nativeOpen(String profiles, String key);
    private native void nativeFree();

    private final long defaultNumBuffers = 3;
    private final long defaultBufferLength = 4096;
    private final int defaultSampleRate = 44100;

    long profile_ptr;
    long numBuffers;
    long bufferLength;
    int sampleRate;
    public FrameTransmitterConfig(Context c, String key, int frameLength) throws IOException, JSONException {
        profile_ptr = nativeOpen(getDefaultProfiles(c, frameLength), key);
        numBuffers = defaultNumBuffers;
        bufferLength = defaultBufferLength;
        sampleRate = defaultSampleRate;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioManager m = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            String pRate = m.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
            sampleRate = Integer.parseInt(pRate);
        }
    }

    public FrameTransmitterConfig(String profiles, String key) {
        profile_ptr = nativeOpen(profiles, key);
        numBuffers = defaultNumBuffers;
        bufferLength = defaultBufferLength;
        sampleRate = defaultSampleRate;
    }

    public static String getDefaultProfiles(Context c, int frameLength) throws IOException, JSONException {
        InputStream s = c.getResources().openRawResource(R.raw.quiet_profiles);
        byte[] profile_bytes = new byte[s.available()];
        s.read(profile_bytes);
        s.close();

        String data = new String(profile_bytes);
        JSONObject profiles = new JSONObject(data);

        Iterator<String> keys = profiles.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject profile = profiles.getJSONObject(key);

            profile.put("frame_length", frameLength);
        }

        data = profiles.toString();
        return data;
    }

    public void setNumBuffers(long numBuffers) {
        this.numBuffers = numBuffers;
    }

    public void setBufferLength(long bufferLength) {
        this.bufferLength = bufferLength;
    }

    public void setSampleRate(int sampleRate) { this.sampleRate = sampleRate; }

    public long getNumBuffers() {
        return numBuffers;
    }

    public long getBufferLength() {
        return bufferLength;
    }

    public int getSampleRate() { return sampleRate; }

    @Override
    protected void finalize() throws Throwable {
        nativeFree();
    }

    static {
        QuietInit.init();
    }
}
