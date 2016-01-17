package com.reconers.cordova.mediaplus;

import android.media.AudioTrack;
import android.os.Handler;
import android.util.Log;

import com.badlogic.gdx.audio.io.Mpg123Decoder;
import com.badlogic.gdx.files.FileHandle;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.apache.cordova.media.AudioPlayer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.vinuxproject.sonic.AndroidAudioDevice;
import org.vinuxproject.sonic.Sonic;

import java.util.HashMap;

/**
 * Created by moonsuhan on 2016. 1. 14..
 */
public class MediaPlus extends CordovaPlugin {

    private static final String LOG_TAG = "MediaPlus";

    HashMap<String, AudioPlayer> players;

    public static final int ISPLAYING = 1;
    public static final int PAUSE = 2;
    public static final int PLAY_END = 3;
    public static final int STOP = 0;

    private static int MEDIA_STATE = 1;
    private static int MEDIA_DURATION = 2;
    private static int MEDIA_POSITION = 3;
    private static int MEDIA_ERROR = 9;


    private CallbackContext messageChannel;

    int channels;

    private int playerState;
    private int currentposition = 0;

    byte samples[] = new byte[10240];
    byte modifiedSamples[] = new byte[5120];

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

            Log.d("MUSIC", "CREATE!!!");
            this.stop();
            this.filepath = args.getString(0).substring(7);

            this.totalSamples = 0;
            this.currentposition = 0;

            this.fileHandle = new FileHandle(filepath);
            this.mpg123Decoder = new Mpg123Decoder(this.fileHandle);

            this.channels = this.mpg123Decoder.getChannels();
            this.totallength = ((int)this.mpg123Decoder.getLength());
            this.rate1 = this.mpg123Decoder.getRate();

            samples = new byte[8092];
            modifiedSamples = new byte[2048];

            this.device = new AndroidAudioDevice(this.rate1, this.channels);
            this.sonic = new Sonic(this.rate1, this.channels);
            this.sonic.setSpeed(this.speed);
            this.sonic.setPitch(this.pitch);
            this.sonic.setVolume(1.0f);

            sendStatusChange(MEDIA_DURATION, null, (float) this.totallength);

            callbackContext.sendPluginResult(new PluginResult(status, "ok"));
            return true;
        }
        else if ("startPlayingAudio".equals(action)) {
            this.play();
            return true;
        }
        else if ("pausePlayingAudio".equals(action)) {
//            this.device.track.pause();
            this.playerState = PAUSE;
//            sendStatusChange(MEDIA_STATE, null, 3.0f);
            return true;
        }
        else if ("stopPlayingAudio".equals(action)) {
            this.stop();
            return true;
        }
        else if ("setRate".equals(action)) {
            this.setSpeed((float) args.getDouble(0));
            return true;
        }
        else if ("getCurrentPositionAudio".equals(action)) {
            callbackContext.sendPluginResult(new PluginResult(status, this.currentposition));
            return true;
        }
        else if ("release".equals(action)) {
            speed = 1.0f;
            this.stop();
            return true;
        }
        else if ("seekToAudio".equals(action)) {
            skip((int) args.getDouble(0));
            return true;
        }
        else if (action.equals("messageChannel")) {
            messageChannel = callbackContext;
            return true;
        }

        return super.execute(action, args, callbackContext);
    }

    public void play()
    {
        this.playerState = ISPLAYING;
        sendStatusChange(MEDIA_STATE, null, 2.0f);

        if (this.thread != null) return;

        this.totalSamples = 0;
        this.currentposition = 0;
        this.mpg123Decoder = new Mpg123Decoder(this.fileHandle);
        this.thread = new Thread(new Runnable() {
            public void run() {

                Log.d("MUSIC", "PLAYING...");

                int bytesRead;
                while(true) {

                    if (playerState == STOP || mpg123Decoder == null) {
                        thread = null;
                        break;
                    }


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
                    if (available > 0) {
                        if (modifiedSamples.length < available) {
                            Log.d("MUSIC", "modifiedSample Reload");
                            modifiedSamples = new byte[available * 2];
                        }
                        sonic.receiveBytes(modifiedSamples, available);
                        if ((device != null) && (device.track.getPlayState() == 3)) {
                            device.writeSamples(modifiedSamples, available);
                        }
                    }

                    currentposition = totalSamples / (rate1 * channels);
                    sendStatusChange(MEDIA_POSITION, null, (float) currentposition);


                    if (bytesRead <= 0) break;
                }

                totalSamples = 0;
                playerState = PLAY_END;
                thread = null;
//                stop();
            }
        });

        this.thread.start();

    }

    public void stop()
    {
        Log.d("MUSIC", "STOP..");
        this.playerState = STOP;
        sendStatusChange(MEDIA_STATE, null, 4.0f);

        if (this.device != null)
        {

            this.device.track.flush();
            this.device.track = null;
            this.device = null;

            if (this.mpg123Decoder != null) {
//                mpg123Decoder.dispose();
                mpg123Decoder = null;
            }

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

    private void sendStatusChange(int messageType, Integer additionalCode, Float value) {

        if (additionalCode != null && value != null) {
            throw new IllegalArgumentException("Only one of additionalCode or value can be specified, not both");
        }

        JSONObject statusDetails = new JSONObject();
        try {
            statusDetails.put("msgType", messageType);
            if (additionalCode != null) {
                JSONObject code = new JSONObject();
                code.put("code", additionalCode.intValue());
                statusDetails.put("value", code);
            }
            else if (value != null) {
                statusDetails.put("value", value.floatValue());
            }
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Failed to create status details", e);
        }

        JSONObject message = new JSONObject();
        try {
            message.put("action", "status");
            if (statusDetails != null) {
                message.put("status", statusDetails);
            }
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Failed to create event message", e);
        }

        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, message);
        pluginResult.setKeepCallback(true);
        if (messageChannel != null) {
            messageChannel.sendPluginResult(pluginResult);
        }

    }

}
