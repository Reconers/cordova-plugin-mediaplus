/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
*/

var argscheck = require('cordova/argscheck'),
    utils = require('cordova/utils'),
    exec = require('cordova/exec');

var mediaObject = null;

/**
 * This class provides access to the device media, interfaces to both sound and video
 *
 * @constructor
 * @param src                   The file name or url to play
 * @param successCallback       The callback to be called when the file is done playing or recording.
 *                                  successCallback()
 * @param errorCallback         The callback to be called if there is an error.
 *                                  errorCallback(int errorCode) - OPTIONAL
 * @param statusCallback        The callback to be called when media status has changed.
 *                                  statusCallback(int statusCode) - OPTIONAL
 */
var MediaPlus = function(src, successCallback, errorCallback, statusCallback) {

    if (mediaObject) {
        mediaObject.release();
    }

    this.successCallback = successCallback;
    this.errorCallback = errorCallback;
    this.statusCallback = statusCallback;
    this._duration = -1;
    this._position = -1;

    mediaObject = this;

    var me = this;

    var fileTransfer = new FileTransfer();
    var uri = encodeURI(src);
    var fileURL = "cdvfile://localhost/temporary/slk_music/" + src.substring(src.lastIndexOf("/") + 1);

    fileTransfer.download(uri, fileURL, function(entry) {
        me.src = entry.toURL();
        exec(successCallback, errorCallback, "MediaPlus", "create", [me.src]);
    }, errorCallback, false, { });

};

// Media messages
MediaPlus.MEDIA_STATE = 1;
MediaPlus.MEDIA_DURATION = 2;
MediaPlus.MEDIA_POSITION = 3;
MediaPlus.MEDIA_ERROR = 9;

// Media states
MediaPlus.MEDIA_NONE = 0;
MediaPlus.MEDIA_STARTING = 1;
MediaPlus.MEDIA_RUNNING = 2;
MediaPlus.MEDIA_PAUSED = 3;
MediaPlus.MEDIA_STOPPED = 4;
MediaPlus.MEDIA_MSG = ["None", "Starting", "Running", "Paused", "Stopped"];

/**
 * Start or resume playing audio file.
 */
MediaPlus.prototype.play = function(options) {
    exec(null, null, "MediaPlus", "startPlayingAudio", [options]);
};

/**
 * Stop playing audio file.
 */
MediaPlus.prototype.stop = function() {
    var me = this;
    exec(function() {
        me._position = 0;
    }, this.errorCallback, "MediaPlus", "stopPlayingAudio", []);
};

/**
 * Seek or jump to a new time in the track..
 */
MediaPlus.prototype.seekTo = function(milliseconds) {
    var me = this;
    exec(function(p) {
        me._position = p;
    }, this.errorCallback, "MediaPlus", "seekToAudio", [milliseconds / 1000]);
};

/**
 * Pause playing audio file.
 */
MediaPlus.prototype.pause = function() {
    exec(null, this.errorCallback, "MediaPlus", "pausePlayingAudio", []);
};

/**
 * Get duration of an audio file.
 * The duration is only set for audio that is playing, paused or stopped.
 *
 * @return      duration or -1 if not known.
 */
MediaPlus.prototype.getDuration = function() {
    return this._duration;
};


MediaPlus.prototype.setRate = function(rate) {
    exec(null, null, "MediaPlus", "setRate", [rate]);
};

/**
 * Get position of audio.
 */
MediaPlus.prototype.getCurrentPosition = function(success, fail) {
    var me = this;
    exec(function(p) {
        me._position = p;
        success(p);
    }, fail, "MediaPlus", "getCurrentPositionAudio", []);
};

/**
 * Release the resources.
 */
MediaPlus.prototype.release = function() {
    exec(null, this.errorCallback, "MediaPlus", "release", []);
};

/**
 * Audio has status update.
 * PRIVATE
 *
 * @param id            The media object id (string)
 * @param msgType       The 'type' of update this is
 * @param value         Use of value is determined by the msgType
 */
MediaPlus.onStatus = function(id, msgType, value) {

    var media = mediaObject;

    if(media) {
        switch(msgType) {
            case Media.MEDIA_STATE :
                media.statusCallback && media.statusCallback(value);
                if(value == Media.MEDIA_STOPPED) {
                    media.successCallback && media.successCallback();
                }
                break;
            case Media.MEDIA_DURATION :
                media._duration = value;
                break;
            case Media.MEDIA_ERROR :
                media.errorCallback && media.errorCallback(value);
                break;
            case Media.MEDIA_POSITION :
                media._position = Number(value);
                break;
            default :
                console.error && console.error("Unhandled Media.onStatus :: " + msgType);
                break;
        }
    }
    else {
         console.error && console.error("Received Media.onStatus callback for unknown media :: " + id);
    }

};



module.exports = MediaPlus;


function onMessageFromNative(msg) {
    if (msg.action == 'status') {
        MediaPlus.onStatus(msg.status.id, msg.status.msgType, msg.status.value);
    } else {
        throw new Error('Unknown media action' + msg.action);
    }
}

if (cordova.platformId === 'android' || cordova.platformId === 'amazon-fireos' || cordova.platformId === 'windowsphone') {

    var channel = require('cordova/channel');

    channel.createSticky('onMediaPlusPluginReady');
    channel.waitForInitialization('onMediaPlusPluginReady');

    channel.onCordovaReady.subscribe(function() {
        exec(onMessageFromNative, undefined, 'MediaPlus', 'messageChannel', []);
        channel.initializationComplete('onMediaPlusPluginReady');
    });
}

