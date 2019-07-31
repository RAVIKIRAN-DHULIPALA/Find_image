package com.ravi.findimage;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequest;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.common.collect.ImmutableList;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.cloud.FirebaseVisionCloudDetectorOptions;
import com.google.firebase.ml.vision.cloud.label.FirebaseVisionCloudLabel;
import com.google.firebase.ml.vision.cloud.label.FirebaseVisionCloudLabelDetector;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.label.FirebaseVisionLabel;
import com.google.firebase.ml.vision.label.FirebaseVisionLabelDetector;
import com.google.firebase.ml.vision.label.FirebaseVisionLabelDetectorOptions;
import com.google.protobuf.ByteString;
import com.ravi.findimage.Helper.InternetCheck;
import com.wonderkiln.camerakit.CameraKit;
import com.wonderkiln.camerakit.CameraKitError;
import com.wonderkiln.camerakit.CameraKitEvent;
import com.wonderkiln.camerakit.CameraKitEventListener;
import com.wonderkiln.camerakit.CameraKitImage;
import com.wonderkiln.camerakit.CameraKitVideo;
import com.wonderkiln.camerakit.CameraView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dmax.dialog.SpotsDialog;

public class MainActivity extends AppCompatActivity {
    CameraView cameraView;
    FloatingActionButton btnDetect, btnflash;
    AlertDialog waitingDialog;
    TextToSpeech t1;
    RelativeLayout r;

    @Override
    protected void onResume() {
        super.onResume();
        cameraView.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraView.stop();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        cameraView = findViewById(R.id.camera_view);
        btnDetect = findViewById(R.id.btn_detect);
        final boolean hasCameraFlash = getPackageManager().
                hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
        boolean isEnabled = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
        waitingDialog = new SpotsDialog.Builder().setContext(this).setMessage("Processing").setCancelable(false).build();
        cameraView.addCameraKitListener(new CameraKitEventListener() {
            @Override
            public void onEvent(CameraKitEvent cameraKitEvent) {
            }

            @Override
            public void onError(CameraKitError cameraKitError) {

            }

            @Override
            public void onImage(CameraKitImage cameraKitImage) {
                waitingDialog.show();
                Bitmap bitmap = cameraKitImage.getBitmap();
                bitmap = Bitmap.createScaledBitmap(bitmap, cameraView.getWidth(), cameraView.getHeight(), false);
                cameraView.stop();
                try{
                    runDetector(bitmap);
                }catch (Exception e){}

            }

            @Override
            public void onVideo(CameraKitVideo cameraKitVideo) {

            }
        });
        btnDetect.setOnClickListener(v -> cameraView.captureImage());
        t1 = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (t1.getEngines().size() == 0) {
                    finish();
                } else {
                    t1.setLanguage(Locale.US);
                }
            }
        });
    }
    private View.OnTouchListener onTouchFlash = new View.OnTouchListener() {
        @Override
        public boolean onTouch(final View view, MotionEvent motionEvent) {
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_UP: {
                    if (cameraView.getFlash() == CameraKit.Constants.FLASH_OFF) {
                        cameraView.setFlash(CameraKit.Constants.FLASH_ON);
                        btnflash.setImageResource( R.drawable.ic_flash_on_black_24dp);
                    } else {
                        cameraView.setFlash(CameraKit.Constants.FLASH_OFF);
                        btnflash.setImageResource(R.drawable.ic_flash_off_black_24dp);
                    }
                    break;
                }
            }
            return true;
        }
    };

    private void runDetector(Bitmap bitmap) {
        final FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(bitmap);
        new InternetCheck(new InternetCheck.Consumer() {
            @Override
            public void accept(boolean internet) {
                if (internet) {
                    FirebaseVisionCloudDetectorOptions options = new FirebaseVisionCloudDetectorOptions.Builder().setModelType(FirebaseVisionCloudDetectorOptions.LATEST_MODEL).setMaxResults(15).build();
                    FirebaseVisionCloudLabelDetector detector = FirebaseVision.getInstance().getVisionCloudLabelDetector();

                    detector.detectInImage(image).addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionCloudLabel>>() {
                        @Override
                        public void onSuccess(List<FirebaseVisionCloudLabel> firebaseVisionCloudLabels) {
                            ProcessDataResult(firebaseVisionCloudLabels);
                        }
                    })
                            .addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    Log.e("ENDmsg", e.getMessage());
                                }
                            });

                } else {
                    FirebaseVisionLabelDetectorOptions options = new FirebaseVisionLabelDetectorOptions.Builder().setConfidenceThreshold(0.8f).build();
                    FirebaseVisionLabelDetector detector = FirebaseVision.getInstance().getVisionLabelDetector(options);
                    detector.detectInImage(image)
                            .addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionLabel>>() {
                                @Override
                                public void onSuccess(List<FirebaseVisionLabel> firebaseVisionLabels) {
                                    ProcessDataResult1(firebaseVisionLabels);
                                }
                            }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.e("Endmsg1", e.getMessage());
                        }
                    });
                }
            }
        });

    }

    private void ProcessDataResult(List<FirebaseVisionCloudLabel> firebaseVisionCloudLabels) {
        try {
            String label = firebaseVisionCloudLabels.get(0).getLabel();
            speak("The Object is " + label);
        } catch (Exception e) {
            speak("Object is out of the vision API Dataset");
        }
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                cameraView.start();
            }
        }, 2000);


        if (waitingDialog.isShowing()) {
            waitingDialog.dismiss();
        }
    }

    private void ProcessDataResult1(List<FirebaseVisionLabel> firebaseVisionLabels) {
        try {
            String label = firebaseVisionLabels.get(0).getLabel();
            speak("The Object is " + label);
        } catch (Exception e) {
            speak("Object is out of the vision API Dataset");
        }
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                cameraView.start();
            }
        }, 2000);
        if (waitingDialog.isShowing()) {
            waitingDialog.dismiss();
        }
    }

    private void speak(String msg) {
        if (Build.VERSION.SDK_INT > 21) {
            t1.speak(msg, TextToSpeech.QUEUE_FLUSH, null, null);
        } else {
            t1.speak(msg, TextToSpeech.QUEUE_FLUSH, null);
        }
    }
}