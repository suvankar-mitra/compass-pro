package net.net16.suvankar.compass;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager mSensorManager;
    private Sensor mMagnet;
    private Sensor mAccelerometer;
    private float[] mGravity;
    private float[] mGeomagnetic;

    //firebase storage
    private StorageReference mStorageRef;
    private File imageFile; //to store the image downloaded

    //UI elements
    private TextView degreeView;
    private ImageView compassView;
    private ImageView arrow;
    private TextView strengthView;
    private ImageView background;

    //to smooth the sensor values
    static final float ALPHA = 0.05f; // if ALPHA = 1 OR 0, no filter applies.

    private static boolean modernSkinActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // remove title
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        //sensor manager
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        // need these to register the sensors in onResume()
        mMagnet = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        degreeView = (TextView) findViewById(R.id.degreeView);
        compassView = (ImageView) findViewById(R.id.compassView);
        strengthView = (TextView) findViewById(R.id.strength);
        arrow = (ImageView) findViewById(R.id.arrow);
        background = (ImageView) findViewById(R.id.background);

        //rescaling the arrow to fit the screen properly
        int width = (int) (getScreenWidth() / 6);
        int height = (int) (getScreenHeight() / 5);
        Bitmap b = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.arrow), width, height, true);
        arrow.setImageBitmap(b);

        //firebase storage
        mStorageRef = FirebaseStorage.getInstance().getReference().child("hi_res_images/background.jpg"); // need exact file name
        getImageFromCloud();
    }

    //get background image from firebase cloud storage
    //and set as background
    private void getImageFromCloud() {
        try {
            final File localFile = File.createTempFile("images", ".jpg");
            mStorageRef.getFile(localFile)
                    .addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                            // Successfully downloaded data to local file
                            Toast.makeText(MainActivity.this, "Background image refreshed", Toast.LENGTH_SHORT).show();
                            background.setImageDrawable(Drawable.createFromPath(localFile.getAbsolutePath()));
                            imageFile = localFile;
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception exception) {
                    // Handle failed download
                    Toast.makeText(MainActivity.this, "Could not download file", Toast.LENGTH_SHORT).show();
                    imageFile = null;
                }
            });
        } catch (IOException ioe) {
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float azimut = 0;
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            mGravity = lowPass(event.values.clone(), mGravity);

        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            mGeomagnetic = lowPass(event.values.clone(), mGeomagnetic);
            float strn = (float) Math.sqrt(mGeomagnetic[0] * mGeomagnetic[0] + mGeomagnetic[1] * mGeomagnetic[1] + mGeomagnetic[2] * mGeomagnetic[2]);
            strengthView.setText((int) strn + " µT");
        }

        if (mGravity != null && mGeomagnetic != null) {
            float R[] = new float[9];
            float I[] = new float[9];

            if (SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic)) {

                // orientation contains azimut, pitch and roll
                float orientation[] = new float[3];
                SensorManager.getOrientation(R, orientation);

                azimut = orientation[0];
            }
        }

        // Do something with this sensor value.
        //degreeView.setText("sensor: "+lux);
        float rotation = (float) (azimut * 360 / (2 * Math.PI));

        int deg = 0;
        if (rotation > 0) {
            deg = (int) rotation;
        } else {
            deg = (int) (360 - (rotation * -1));
        }

        if (deg < 22 || deg >= 338) {
            degreeView.setText("North " + deg + "\u00B0");
        } else if (deg >= 22 && deg < 67) {
            degreeView.setText("North East " + deg + "\u00B0");
        } else if (deg >= 67 && deg < 112) {
            degreeView.setText("East " + deg + "\u00B0");
        } else if (deg >= 112 && deg < 157) {
            degreeView.setText("South East " + deg + "\u00B0");
        } else if (deg >= 157 && deg < 202) {
            degreeView.setText("South " + deg + "\u00B0");
        } else if (deg >= 202 && deg < 247) {
            degreeView.setText("South West " + deg + "\u00B0");
        } else if (deg >= 247 && deg < 292) {
            degreeView.setText("West " + deg + "\u00B0");
        } else if (deg >= 292 && deg < 338) {
            degreeView.setText("North West " + deg + "\u00B0");
        }

        //rotate the compass image according to the rotation
        compassView.setRotation(-rotation);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if(accuracy<3) {
            Toast.makeText(this, "Please calibrate your device!", Toast.LENGTH_SHORT).show();
        }
    }

    // https://en.wikipedia.org/wiki/Low-pass_filter
    // to smooth the sensor values
    public static float[] lowPass(float[] input, float[] output) {
        if (output == null) return input;

        for (int i = 0; i < input.length; i++) {
            output[i] = output[i] + ALPHA * (input[i] - output[i]);
        }
        return output;
    }

    public static int getScreenWidth() {
        return Resources.getSystem().getDisplayMetrics().widthPixels;
    }

    public static int getScreenHeight() {
        return Resources.getSystem().getDisplayMetrics().heightPixels;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mMagnet, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this, mMagnet);
        mSensorManager.unregisterListener(this, mAccelerometer);
    }

    public void drawInfoFragment(View view) {
        FragmentManager fm = getSupportFragmentManager();
        fm.beginTransaction().setCustomAnimations(R.anim.frag_enter_from_right, R.anim.frag_exit_to_left,
                R.anim.frag_enter_from_left, R.anim.frag_exit_to_right)
                .addToBackStack(this.getLocalClassName())
                .replace(android.R.id.content, new InfoFragment())
                .commit();
    }

    public void closeInfoFragment(View view) {
        getSupportFragmentManager().popBackStack();
    }

    public void refreshBackground(View view) {
        Toast.makeText(this, "Refreshing background image...", Toast.LENGTH_SHORT).show();
        getImageFromCloud();
    }

    public void setAsWallpaper(final View view) {
        if (imageFile == null) {
            Toast.makeText(this, "Image is not available. Please check your internet connection.", Toast.LENGTH_SHORT).show();
            return;
        }
        final WallpaperManager myWallpaperManager
                = WallpaperManager.getInstance(view.getContext());

        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        //Yes button clicked -> setting the wallpaper
                        dialog.dismiss();
                        try {
                            myWallpaperManager.setBitmap(BitmapFactory.decodeFile(imageFile.getAbsolutePath()));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        Toast.makeText(getApplicationContext(),"Wallpaper set",Toast.LENGTH_SHORT);
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        //No button clicked
                        break;
                }
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Set wallpaper")
                .setMessage("Do you want to set this image as your phone's homescreen wallpaper?")
                .setPositiveButton("Yes", dialogClickListener)
                .setNegativeButton("Cancel", dialogClickListener)
                .show();
    }

    public void shareImage(View view) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(imageFile));
        Log.d("share",Uri.fromFile(imageFile).toString());
        sendIntent.setType("image/*");
        startActivity(Intent.createChooser(sendIntent,"Share"));
    }


}
