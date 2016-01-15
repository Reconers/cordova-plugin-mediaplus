package com.reconers.mediaplus;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.badlogic.gdx.audio.io.Mpg123Decoder;
import com.badlogic.gdx.files.FileHandle;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.vinuxproject.sonic.AndroidAudioDevice;
import org.vinuxproject.sonic.Sonic;

import java.io.File;

/**
 * Created by moonsuhan on 2016. 1. 14..
 */
public class MediaPlus extends CordovaPlugin {


    public static final int ISPLAYING = 1;
    public static final int PAUSE = 2;
    public static final int PLAY_END = 3;
    public static final int RESOLVE = 4;
    public static final int STOP = 0;

    int channels;

    private int playerState;
    private int currentposition = 0;

    byte samples[] = new byte[8092];
    byte modifiedSamples[] = new byte[2048];

    AndroidAudioDevice device;
    Sonic sonic;

    String filepath = "";
    FileHandle fileHandle;
    Mpg123Decoder mpg123Decoder;

    private boolean isChange = false;
    private Handler mhandler;

    float pitch = 1.0F;
    float rate = 1.0F;
    int rate1;

    int skipAmount = 0;
    float speed = 1.0F;

    private Thread thread;

    int totalSamples = 0;
    private int totallength;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);


    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        Log.d("MediaPlus", "EXECUTE : " + action);

        PluginResult.Status status = PluginResult.Status.OK;

        if ("create".equals(action)) {
            this.playerState = STOP;
            this.filepath = args.getString(0).substring(7);

            this.totalSamples = 0;
            this.currentposition = 0;

            this.fileHandle = new FileHandle(filepath);
            this.mpg123Decoder = new Mpg123Decoder(this.fileHandle);

            this.channels = this.mpg123Decoder.getChannels();
            this.totallength = ((int)this.mpg123Decoder.getLength());
            this.rate1 = this.mpg123Decoder.getRate();

            this.device = new AndroidAudioDevice(this.rate1, this.channels);
            this.sonic = new Sonic(this.rate1, this.channels);
            this.sonic.setSpeed(this.speed);
            this.sonic.setPitch(this.pitch);
            this.sonic.setVolume(1.0f);

            callbackContext.sendPluginResult(new PluginResult(status, "ok"));
        }
        else if ("startPlayingAudio".equals(action)) {
            this.play();
        }
        else if ("pausePlayingAudio".equals(action)) {
            this.playerState = PAUSE;
        }
        else if ("stopPlayingAudio".equals(action)) {
            this.stop();
        }
        else if ("setRate".equals(action)) {
            this.setSpeed((float) args.getDouble(0));
        }
        else if ("getCurrentPositionAudio".equals(action)) {
            callbackContext.sendPluginResult(new PluginResult(status, this.currentposition));
        }
        else if ("release".equals(action)) {
            this.stop();
            this.sonic.close();
            this.mpg123Decoder.dispose();
            this.fileHandle.delete();
        }
        else if ("seekToAudio".equals(action)) {
            this.skip(args.getInt(0));
        }

        return super.execute(action, args, callbackContext);
    }

    public void play()
    {
        this.playerState = ISPLAYING;

        if (this.thread != null) return;

        this.thread = new Thread(new Runnable() {
            public void run() {

                Log.d("MUSIC", "PLAYING...");

                int bytesRead;
                while(true) {
                    if (playerState == PAUSE) continue;

                    if (skipAmount != 0) {
                        int i = skipAmount;
                        skipAmount = 0;
                        Log.d("MUSIC", "VALUE : " + String.valueOf(totalSamples + rate1 * channels * i));
                        totalSamples = mpg123Decoder.skipSamples(totalSamples + rate1 * channels * i);
                    }

                    bytesRead = mpg123Decoder.readSamples(samples, 0, samples.length);
                    totalSamples += bytesRead / 2;

                    if (bytesRead > 0) {
                        sonic.putBytes(samples, bytesRead);
                    }
                    else {
                        sonic.flush();
                    }

                    int available = sonic.availableBytes();
                    if (available > 0)
                    {
                        if (modifiedSamples.length < available) {
                            modifiedSamples = new byte[available * 2];
                        }
                        sonic.receiveBytes(modifiedSamples, available);
                        if ((device != null) && (device.track.getPlayState() == 3)) {
                            device.writeSamples(modifiedSamples, available);
                        }
                    }

                    currentposition = totalSamples / (rate1 * channels);
                    Log.d("MEDIA", String.valueOf(currentposition));
                    if (bytesRead <= 0) break;
                }

                device.flush();
                playerState = PLAY_END;
                stop();
            }
        });

        this.thread.start();

    }

    public void stop()
    {
        this.playerState = STOP;
        if (this.device != null)
        {
            this.device.track.flush();
            this.device.track.stop();
            this.device.track.release();
            this.device.track = null;
            this.device = null;
        }
        this.thread = null;
    }

    public void skip(int skip)
    {
        this.skipAmount += skip - this.currentposition;
        this.isChange = true;
    }

    public void setPlayerState(int state)
    {
        this.playerState = state;
    }

    public void setSpeed(float speed)
    {
        this.speed = speed;
        if (this.sonic != null) this.sonic.setSpeed(speed);
    }


}
