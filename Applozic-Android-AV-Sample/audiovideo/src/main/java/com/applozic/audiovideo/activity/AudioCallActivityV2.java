package com.applozic.audiovideo.activity;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.applozic.audiovideo.authentication.Dialog;
import com.applozic.audiovideo.authentication.MakeAsyncRequest;
import com.applozic.audiovideo.authentication.Token;
import com.applozic.audiovideo.authentication.TokenGeneratorCallback;
import com.applozic.mobicomkit.api.MobiComKitConstants;
import com.applozic.mobicomkit.api.account.user.MobiComUserPreference;
import com.applozic.mobicomkit.api.conversation.MessageIntentService;
import com.applozic.mobicomkit.api.conversation.MobiComMessageService;
import com.applozic.mobicomkit.api.notification.VideoCallNotificationHelper;
import com.applozic.mobicomkit.broadcast.BroadcastService;
import com.applozic.mobicomkit.broadcast.ConnectivityReceiver;
import com.applozic.mobicomkit.contact.AppContactService;
import com.applozic.mobicommons.commons.core.utils.Utils;
import com.applozic.mobicommons.commons.image.ImageLoader;
import com.applozic.mobicommons.json.GsonUtils;
import com.applozic.mobicommons.people.contact.Contact;
import com.twilio.video.AudioOutput;
import com.twilio.video.AudioTrack;
import com.twilio.video.CameraCapturer;
import com.twilio.video.ConnectOptions;
import com.twilio.video.LocalAudioTrack;
import com.twilio.video.LocalMedia;
import com.twilio.video.LocalVideoTrack;
import com.twilio.video.Media;
import com.twilio.video.Participant;
import com.twilio.video.Room;
import com.twilio.video.RoomState;
import com.twilio.video.VideoClient;
import com.twilio.video.VideoException;
import com.twilio.video.VideoTrack;
import com.twilio.video.VideoView;

import java.util.Map;

import applozic.com.audiovideo.R;

/**
 * Created by Adarsh on 12/15/16.
 */

public class AudioCallActivityV2 extends AppCompatActivity implements TokenGeneratorCallback{
    private static final int CAMERA_MIC_PERMISSION_REQUEST_CODE = 1;
    private static final String TAG = "AudioCallActivityV2";
    public static final long IN_COMING_CALL_TIMEOUT = 30 * 1000L;

    /*
     * The Video Client allows a client to connect to a room
     */
    private VideoClient videoClient;

    /*
     * A Room represents communication between the client and one or more participants.
     */
    private Room room;

    /*
     * A VideoView receives frames from a local or remote video track and renders them
     * to an associated view.
     */
    protected VideoView primaryVideoView;
    protected VideoView thumbnailVideoView;

    /*
     * Android application UI elements
     */
    protected TextView videoStatusTextView;
    protected CameraCapturer cameraCapturer;
    private LocalMedia localMedia;
    protected LocalAudioTrack localAudioTrack;
    protected LocalVideoTrack localVideoTrack;
    protected VideoView localVideoView;
    protected FloatingActionButton connectActionFab;
    protected FloatingActionButton switchCameraActionFab;
    protected FloatingActionButton localVideoActionFab;
    protected FloatingActionButton muteActionFab;
    private android.support.v7.app.AlertDialog alertDialog;
    protected AudioManager audioManager;
    private String participantIdentity;

    private int previousAudioMode;

    // ************** APPLOZIC NOTIFICATION AND PROFILE ***************************//

    protected VideoCallNotificationHelper videoCallNotificationHelper;
    protected Contact contactToCall;
    protected Token token;
    protected boolean answered;
    protected ProgressDialog progress;
    protected MobiComMessageService messageService;
    protected BroadcastReceiver applozicBroadCastReceiver;
    protected boolean incomingCall;
    protected boolean inviteSent;
    protected String callId;
    protected long callStartTime;
    protected boolean autoCall = false;
    protected MediaPlayer mediaPlayer;
    protected AppContactService contactService;
    protected TextView contactName;
    protected ImageView profileImage;
    protected boolean pauseVideo;


    ImageLoader mImageLoader;
    CountDownTimer timer;
    TextView txtCount;
    private int cnt;

    protected boolean videoCall = false;
    protected FloatingActionButton speakerActionFab;

    public AudioCallActivityV2() {
        this.videoCall = false;
    }

    public AudioCallActivityV2(boolean videoCall) {
        this.videoCall = videoCall;
    }


    public static void setOpenStatus(boolean isInOpenStatus) {
        BroadcastService.videoCallAcitivityOpend = isInOpenStatus;
    }

    int  rejectClickCount;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setOpenStatus(true);

        /*
         * Set the initial state of the UI
         */
        if(videoCall){
            System.out.println(" video call returning ...");
            return;
        }
        setContentView(R.layout.applozic_audio_call);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        init();

        contactName = (TextView) findViewById(R.id.contact_name);
        profileImage = (ImageView) findViewById(R.id.applozic_audio_profile_image);
        txtCount = (TextView) findViewById(R.id.applozic_audio_timer);

        contactName.setText(contactToCall.getDisplayName());
        pauseVideo = true;

        mImageLoader = new ImageLoader(this, profileImage.getHeight()) {
            @Override
            protected Bitmap processBitmap(Object data) {
                return contactService.downloadContactImage(AudioCallActivityV2.this, (Contact) data);
            }
        };
        mImageLoader.setLoadingImage(R.drawable.applozic_ic_contact_picture_holo_light);
        // Add a cache to the image loader
        mImageLoader.setImageFadeIn(false);
        mImageLoader.loadImage(contactToCall, profileImage);

        primaryVideoView = (VideoView) findViewById(R.id.primary_video_view);
        thumbnailVideoView = (VideoView) findViewById(R.id.thumbnail_video_view);

        //Video Status Text view, for debug only
        videoStatusTextView = (TextView) findViewById(R.id.video_status_textview);
        videoStatusTextView.setVisibility(View.GONE);

        connectActionFab = (FloatingActionButton) findViewById(R.id.call_action_fab);
        muteActionFab = (FloatingActionButton) findViewById(R.id.mute_action_fab);
        speakerActionFab = (FloatingActionButton) findViewById(R.id.speaker_action_fab);

        /*
         * Needed for setting/abandoning audio focus during call
         */
        audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);

        /*
         * Check camera and microphone permissions. Needed in Android M.
         */
        if (!checkPermissionForCameraAndMicrophone()) {
            requestPermissionForCameraAndMicrophone();
        } else {
            createLocalMedia();
            intializeUI();
            initializeApplozic();
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == CAMERA_MIC_PERMISSION_REQUEST_CODE) {
            boolean cameraAndMicPermissionGranted = true;

            for (int grantResult : grantResults) {
                cameraAndMicPermissionGranted &= grantResult == PackageManager.PERMISSION_GRANTED;
            }

            if (cameraAndMicPermissionGranted) {

                createLocalMedia();
                intializeUI();
                initializeApplozic();

            } else {

                Toast.makeText(this,
                        R.string.permissions_needed,
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        /*
         * Release local media when no longer needed
         */
        if (localMedia != null) {
            localMedia.release();
            localMedia = null;
        }
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(applozicBroadCastReceiver);
        super.onDestroy();
        setOpenStatus(false);
    }

    protected boolean checkPermissionForCameraAndMicrophone(){
        int resultCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        int resultMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        return resultCamera == PackageManager.PERMISSION_GRANTED &&
                resultMic == PackageManager.PERMISSION_GRANTED;
    }

    protected void requestPermissionForCameraAndMicrophone(){
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) ||
                ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.RECORD_AUDIO)) {
            Toast.makeText(this,
                    R.string.permissions_needed,
                    Toast.LENGTH_LONG).show();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                    CAMERA_MIC_PERMISSION_REQUEST_CODE);
        }
    }

    //TODO: get this and reference
    protected void createLocalMedia() {
        localMedia = LocalMedia.create(this);

        // Share your microphone
        localAudioTrack = localMedia.addAudioTrack(true);

        // Share your camera
        if(videoCall){
            cameraCapturer = new CameraCapturer(this, CameraCapturer.CameraSource.FRONT_CAMERA);
            localVideoTrack = localMedia.addVideoTrack(true, cameraCapturer);
            primaryVideoView.setMirror(true);
            localVideoTrack.addRenderer(primaryVideoView);
            localVideoView = primaryVideoView;
        }else{
            Log.i(TAG,"Not initialising camera...");
        }

    }


    //TODO: get this and connectToRoom
    private void connectToRoom(String roomName) {
        setAudioFocus(true);
        ConnectOptions connectOptions = new ConnectOptions.Builder()
                .roomName(roomName)
                .localMedia(localMedia)
                .build();
        if(videoClient!=null){
            room = videoClient.connect(connectOptions, roomListener());
            setDisconnectAction();
        }

    }

    /*
     * The initial state when there is no active conversation.
     */
    protected void intializeUI() {
        connectActionFab.setImageDrawable(ContextCompat.getDrawable(this,
                R.drawable.ic_call_white_24px));
        connectActionFab.show();
        connectActionFab.setOnClickListener(connectActionClickListener());
        if(videoCall){
            switchCameraActionFab.show();
            switchCameraActionFab.setOnClickListener(switchCameraClickListener());
            localVideoActionFab.show();
            localVideoActionFab.setOnClickListener(localVideoClickListener());
        }
        muteActionFab.show();
        muteActionFab.setOnClickListener(muteClickListener());
        speakerActionFab.setOnClickListener(speakerClickListener());
    }

    /*
     * The actions performed during disconnect.
     */
    protected void setDisconnectAction() {
        connectActionFab.setImageDrawable(ContextCompat.getDrawable(this,
                R.drawable.ic_call_end_white_24px));
        connectActionFab.show();
        connectActionFab.setOnClickListener(disconnectClickListener());
    }


    /*
     * Called when participant joins the room
     */
    private void addParticipant(Participant participant) {
        /*
         * This app only displays video for one additional participant per Room
         */
        if (thumbnailVideoView.getVisibility() == View.VISIBLE) {
            Snackbar.make(connectActionFab,
                    "Multiple participants are not currently support in this UI",
                    Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            return;
        }
        videoCallNotificationHelper.sendVideoCallAnswer(contactToCall,callId);
        participantIdentity = participant.getIdentity();
        videoStatusTextView.setText("Participant "+ participantIdentity + " joined");
        /*
         * Stop rendering local video track in primary view and move it to thumbnail view
         */
        if(videoCall){
            localVideoTrack.removeRenderer(primaryVideoView);
            thumbnailVideoView.setVisibility(View.VISIBLE);
            localVideoTrack.addRenderer(thumbnailVideoView);
            localVideoView = thumbnailVideoView;
        }

        /*
         * Start listening for participant media events
         */
        participant.getMedia().setListener(mediaListener());

    }

    /*
     * Called when participant leaves the room
     */
    private void removeParticipant(Participant participant) {
        videoStatusTextView.setText("Participant "+participant.getIdentity()+ " left.");
        if (!participant.getIdentity().equals(participantIdentity)) {
            return;
        }
        /*
         * Show local video in primary view
         */
        if(videoCall){
            thumbnailVideoView.setVisibility(View.GONE);
            localVideoTrack.removeRenderer(thumbnailVideoView);
            primaryVideoView.setMirror(true);
            localVideoTrack.addRenderer(primaryVideoView);
            localVideoView = primaryVideoView;
        }

    }

    /*
     * Room events listener
     */
    private Room.Listener roomListener() {

        return new Room.Listener() {
            @Override
            public void onConnected(Room room) {
                videoStatusTextView.setText("Connected to " + room.getName());
                setTitle(room.getName());
                setSpeakerphoneOn(videoCall);
                for (Map.Entry<String, Participant> entry : room.getParticipants().entrySet()) {
                    addParticipant(entry.getValue());
                    hideProgress();
                    if(!videoCall){
                        timer.start();
                    }
                    break;
                }
            }

            @Override
            public void onConnectFailure(Room room, VideoException e) {
                inviteSent=false;
                videoStatusTextView.setText("Failed to connect");
                hideProgress();
                finish();
            }

            @Override
            public void onDisconnected(Room room, VideoException e) {
                videoStatusTextView.setText("Disconnected from " + room.getName());
                AudioCallActivityV2.this.room = null;
                setAudioFocus(false);
                if(!videoCall){
                    timer.cancel();
                }
                if(!incomingCall && callStartTime>0){
                    long diff =  (System.currentTimeMillis() - callStartTime);
                    videoCallNotificationHelper.sendVideoCallEnd(contactToCall,callId,String.valueOf(diff));
                }
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        finish();
                    }
                }, 2000);
            }

            @Override
            public void onParticipantConnected(Room room, Participant participant) {
                addParticipant(participant);
                hideProgress();
                if(!videoCall){
                    timer.start();
                }
                if(!incomingCall){
                    callStartTime = System.currentTimeMillis();
                }
            }

            @Override
            public void onParticipantDisconnected(Room room, Participant participant) {
                removeParticipant(participant);
                disconnectAndExit();
            }
        };
    }

    private Media.Listener mediaListener() {
        return new Media.Listener() {

            @Override
            public void onAudioTrackAdded(Media media, AudioTrack audioTrack) {
                videoStatusTextView.setText("onAudioTrackAdded");
            }

            @Override
            public void onAudioTrackRemoved(Media media, AudioTrack audioTrack) {
                videoStatusTextView.setText("onAudioTrackRemoved");
            }

            @Override
            public void onVideoTrackAdded(Media media, VideoTrack videoTrack) {
                videoStatusTextView.setText("onVideoTrackAdded");
                /*
                 * Set primary view as renderer for participant video track
                 */
                primaryVideoView.setMirror(false);
                videoTrack.addRenderer(primaryVideoView);
            }

            @Override
            public void onVideoTrackRemoved(Media media, VideoTrack videoTrack) {
                videoStatusTextView.setText("onVideoTrackRemoved");
                videoTrack.removeRenderer(primaryVideoView);
            }

            @Override
            public void onAudioTrackEnabled(Media media, AudioTrack audioTrack) {

            }

            @Override
            public void onAudioTrackDisabled(Media media, AudioTrack audioTrack) {

            }

            @Override
            public void onVideoTrackEnabled(Media media, VideoTrack videoTrack) {

            }

            @Override
            public void onVideoTrackDisabled(Media media, VideoTrack videoTrack) {

            }
        };
    }


    private View.OnClickListener disconnectClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //invite sent but NOT Yet connected,
                if( inviteSent && participantIdentity==null ){

                    inviteSent=false;
                    videoCallNotificationHelper.sendCallMissed(contactToCall, callId);
                    videoCallNotificationHelper.sendVideoCallMissedMessage(contactToCall, callId);

                }
               disconnectAndExit();
            }
        };
    }

    private void disconnectAndExit() {
        if (room != null) {
            room.disconnect();
        }
    }

    private View.OnClickListener connectActionClickListener() {
        return new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                //Send request to same userId...
            }
        };
    }

    private DialogInterface.OnClickListener cancelConnectDialogClickListener() {
        return new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                intializeUI();
                alertDialog.dismiss();
            }
        };
    }

    private View.OnClickListener switchCameraClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cameraCapturer != null) {
                    cameraCapturer.switchCamera();
                    localVideoView.setMirror(
                            cameraCapturer.getCameraSource() ==
                                    CameraCapturer.CameraSource.FRONT_CAMERA);
                }
            }
        };
    }

    private View.OnClickListener localVideoClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                 * Enable/disable the local video track
                 */
                if (localVideoTrack != null) {
                    boolean enable = !localVideoTrack.isEnabled();
                    localVideoTrack.enable(enable);
                    int icon;
                    if (enable) {
                        icon = R.drawable.ic_videocam_green_24px;
                        switchCameraActionFab.show();
                    } else {
                        icon = R.drawable.ic_videocam_off_red_24px;
                        switchCameraActionFab.hide();
                    }
                    localVideoActionFab.setImageDrawable(
                            ContextCompat.getDrawable(AudioCallActivityV2.this, icon));
                }
            }
        };
    }

    private View.OnClickListener muteClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                 * Enable/disable the local audio track
                 */
                if (localAudioTrack != null) {
                    boolean enable = !localAudioTrack.isEnabled();
                    localAudioTrack.enable(enable);
                    int icon = enable ?
                            R.drawable.ic_mic_green_24px : R.drawable.ic_mic_off_red_24px;
                    muteActionFab.setImageDrawable(ContextCompat.getDrawable(
                            AudioCallActivityV2.this, icon));
                }
            }
        };
    }

    private void retrieveAccessTokenfromServer(Token token) {

        String accessToken = token.getToken();
        videoClient = new VideoClient(this, accessToken);
        initiateCall();
    }

    private void setAudioFocus(boolean focus) {
        if (focus) {
            previousAudioMode = audioManager.getMode();
            // Request audio focus before making any device switch.
            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            /*
             * Use MODE_IN_COMMUNICATION as the default audio mode. It is required
             * to be in this mode when playout and/or recording starts for the best
             * possible VoIP performance. Some devices have difficulties with
             * speaker mode if this is not set.
             */
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        } else {
            audioManager.setMode(previousAudioMode);
            audioManager.abandonAudioFocus(null);
        }
    }

    @Override
    public void onNetworkComplete(String response) {
        Log.i(TAG, "Token response: " + response);
        if (TextUtils.isEmpty(response)) {
            Log.i(TAG, "Not able to get token");
            return;
        }

        Token token = (Token) GsonUtils.getObjectFromJson(response, Token.class);
        MobiComUserPreference.getInstance(this).setVideoCallToken(token.getToken());
        retrieveAccessTokenfromServer(token);
    }

    public void initializeApplozic() {
         /*
         * Enable changing the volume using the up/down keys during a conversation
         */
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        /*
         * Needed for setting/abandoning audio focus during call
         */
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mediaPlayer = MediaPlayer.create(this, R.raw.hangouts_video_call);
        mediaPlayer.setLooping(true);

        if (!Utils.isInternetAvailable(this)) {
            Toast toast = Toast.makeText(this, getString(R.string.internet_connection_not_available), Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
            finish();
            return;
        }

         /*
         * Check camera and microphone permissions. Needed in Android M.
         */
        if (!checkPermissionForCameraAndMicrophone()) {
            requestPermissionForCameraAndMicrophone();
        } else {
            if (incomingCall) {
                progress = new ProgressDialog(this);
                progress.setMessage("Connecting...");
                progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                progress.setIndeterminate(true);
                progress.setCancelable(false);
                progress.show();
                scheduleStopRinging(callId);
            }else{
                mediaPlayer.start();
            }

            LocalBroadcastManager.getInstance(this).registerReceiver(applozicBroadCastReceiver,
                    BrodCastIntentFilters());
        }

        timer = initializeTimer();
        startCallWithAcessToken();
    }

    static IntentFilter BrodCastIntentFilters() {
        IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(MobiComKitConstants.APPLOZIC_VIDEO_CALL_REJECTED);
        intentFilter.addAction(VideoCallNotificationHelper.CALL_CANCELED);
        intentFilter.addAction(VideoCallNotificationHelper.CALL_END);
        intentFilter.addAction(MobiComKitConstants.APPLOZIC_VIDEO_DIALED);
        intentFilter.addAction(VideoCallNotificationHelper.CALL_MISSED);
        intentFilter.addAction(ConnectivityReceiver.CONNECTIVITY_CHANGE);

        return intentFilter;
    }

    @NonNull
    public CountDownTimer initializeTimer() {
        return new CountDownTimer(Long.MAX_VALUE, 1000) {

            @Override
            public void onTick(long millisUntilFinished) {

                cnt++;
               // long millis = cnt;
                int seconds = (cnt);
                int hrs =  seconds/(60*60);
                int minutes = seconds/60;
                seconds = seconds%60;
                txtCount.setText(String.format("%d:%02d:%02d", hrs, minutes, seconds));
            }

            @Override
            public void onFinish() {

            }
        };
    }

    protected void init() {

        Intent intent = getIntent();
        String contactId = intent.getStringExtra("CONTACT_ID");
        Log.i(TAG, "ContactId: " + contactId);
        contactService = new AppContactService(this);
        messageService = new MobiComMessageService(this, MessageIntentService.class);
        Log.i(TAG, "init():::" + this.videoCall);
        videoCallNotificationHelper = new VideoCallNotificationHelper(this, !this.videoCall);

        contactToCall = contactService.getContactById(contactId);
        incomingCall = intent.getBooleanExtra("INCOMING_CALL", Boolean.FALSE);
        callId = intent.getStringExtra("CALL_ID");
        registerForNotificationBroadcast();

    }

    private void startCallWithAcessToken() {

        //Token generations ..
        String  accessTokenValue = null;
                //MobiComUserPreference.getInstance(this).getVideoCallToken();

        if( TextUtils.isEmpty(accessTokenValue)  ){

            MakeAsyncRequest asyncTask = new MakeAsyncRequest(this, this);
            asyncTask.execute((Void) null);

        }else{

            Token tokenObj = new Token(MobiComUserPreference.getInstance(this).getUserId(),accessTokenValue);
            retrieveAccessTokenfromServer(tokenObj);

        }

    }

    public void initiateCall() {
        if(incomingCall){
            connectToRoom(callId);
        }else{
            sendInvite();
            inviteSent = true;
        }

    }

    public void registerForNotificationBroadcast() {

        applozicBroadCastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                String incomingCallId = intent.getStringExtra(VideoCallNotificationHelper.CALL_ID);
                boolean isNotificationForSameId = false;

                Log.i(TAG, "incomingCallId: " + incomingCallId + ", intent.getAction(): " + intent.getAction());

                if(ConnectivityReceiver.CONNECTIVITY_CHANGE.equals(intent.getAction())){
                    if(!Utils.isInternetAvailable(context)){
                        Toast.makeText(context,"No network connectivity.",Toast.LENGTH_LONG);
                        if(room!=null && room.getState().equals(RoomState.CONNECTED)){
                            room.disconnect();
                        }
                    }
                    return;
                }

                if (!TextUtils.isEmpty(callId)) {
                    isNotificationForSameId = (callId.equals(incomingCallId));
                }
//                //
//                if (MobiComKitConstants.APPLOZIC_VIDEO_CALL_ANSWER.equals(intent.getAction()) && isNotificationForSameId) {
//                    answered = true;
//                    sendInvite();
//                } else
                 if ((MobiComKitConstants.APPLOZIC_VIDEO_CALL_REJECTED.equals(intent.getAction()) ||
                        VideoCallNotificationHelper.CALL_CANCELED.equals(intent.getAction()) ||
                        VideoCallNotificationHelper.CALL_MISSED.equals(intent.getAction()) ||
                         VideoCallNotificationHelper.CALL_END.equals(intent.getAction()))
                        && isNotificationForSameId) {

                    Toast.makeText(context, "Participant is busy..", Toast.LENGTH_LONG).show();
                    hideProgress();
                     if(room!=null){
                         inviteSent=false;
                         room.disconnect();
                     }
                 } else if (MobiComKitConstants.APPLOZIC_VIDEO_DIALED.equals(intent.getAction())) {

                    String contactId = intent.getStringExtra("CONTACT_ID");

                    if (!contactId.equals(contactToCall.getUserId()) || ( room != null && room.getState().equals(RoomState.CONNECTED))) {
                        Contact contact = contactService.getContactById(contactId);
                        videoCallNotificationHelper.sendVideoCallReject(contact, incomingCallId);
                        return;
                    }
                    callId = incomingCallId;
                     connectToRoom(callId);
                }
            }
        };
    }

    protected void sendInvite() {

        if(videoCall){
            callId = videoCallNotificationHelper.sendVideoCallRequest(contactToCall);
        }else{
            callId = videoCallNotificationHelper.sendAudioCallRequest(contactToCall);
        }
        scheduleStopRinging(callId);
        connectToRoom(callId);
        setDisconnectAction();

    }

    public void scheduleStopRinging(final String callIdScheduled) {
        final Context context = this;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                long timeDuration = incomingCall ? IN_COMING_CALL_TIMEOUT : VideoCallNotificationHelper.MAX_NOTIFICATION_RING_DURATION + 10 * 1000;
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        //Check for incoming call if
                        if( incomingCall && participantIdentity==null ){
                            Toast.makeText(context, "Connection error..", Toast.LENGTH_LONG).show();
                            hideProgress();
                            disconnectAndExit();
                            return;
                        }

                        if( isScheduleStopRequire() ){

                            videoCallNotificationHelper.sendCallMissed(contactToCall, callId);
                            videoCallNotificationHelper.sendVideoCallMissedMessage(contactToCall, callId);
                            Toast.makeText(context, "No answers..", Toast.LENGTH_LONG).show();
                            hideProgress();
                            disconnectAndExit();
                        }
                    }
                }, timeDuration);
            }
        });
    }

    private boolean isScheduleStopRequire() {

      return  ( inviteSent &&
        ( participantIdentity==null || !participantIdentity.equals(contactToCall.getUserId())) );
    }

    protected void hideProgress() {
        try {
            Log.i(TAG, "Hiding progress dialog.");
            if (alertDialog != null && alertDialog.isShowing()) {
                alertDialog.dismiss();
            }
            if (progress != null && progress.isShowing()) {
                progress.dismiss();
            }
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    @Override
    public void onBackPressed() {

        //Connected....ROOM
        if (room!=null && room.getState().equals(RoomState.CONNECTED)) {
            alertDialog = Dialog.createCloseSessionDialog(new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Log.i(TAG, "onBackPressed cancel do nothing.. ");
                }
            }, closeSessionListener(), this);
            alertDialog.show();

        } else if( room!=null && !room.getState().equals(RoomState.CONNECTED) ) {
            //DO nothing....
        }
        else {
            super.onBackPressed();
        }

    }


    private DialogInterface.OnClickListener closeSessionListener() {

        return new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                room.disconnect();
            }
        };
    }


    private View.OnClickListener speakerClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
             /*
                 * Audio routing to speakerphone or headset
                 */
                if (room == null) {
                    Log.e(TAG, "Unable to set audio output, conversation client is null");
                    return;
                }
                setSpeakerphoneOn(!(videoClient.getAudioOutput() == AudioOutput.SPEAKERPHONE));
            }
        };
    }

    private void setSpeakerphoneOn(boolean on) {
        if (room == null) {
            Log.e(TAG, "Unable to set audio output, conversation client is null");
            return;
        }
        try {
            if (videoClient != null) {
                videoClient.setAudioOutput(on ? AudioOutput.SPEAKERPHONE : AudioOutput.HEADSET);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        if (on) {
            Drawable drawable = ContextCompat.getDrawable(this,
                    R.drawable.ic_volume_down_green_24px);
            speakerActionFab.setImageDrawable(drawable);
        } else {
            // route back to headset
            Drawable drawable = ContextCompat.getDrawable(this,
                    R.drawable.ic_volume_down_white_24px);
            speakerActionFab.setImageDrawable(drawable);
        }
    }

}

