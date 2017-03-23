package net.net16.suvankar.compass;

import android.Manifest;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SensorEventListener, LocationListener {

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
    private ImageView locationView;

    //to smooth the sensor values
    static final float ALPHA = 0.05f; // if ALPHA = 1 OR 0, no filter applies.
    
    //to calculate true north
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private LocationManager locationManager;
    private String provider;
    private boolean userPermission = false;
    private float latitude, longitude, altitude;
    private GeomagneticField mGeomagneticField;
    private double declination;
    private boolean locationServiceOff = true;

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
        locationView = (ImageView) findViewById(R.id.location);

        //rescaling the arrow to fit the screen properly
        int width = (int) (getScreenWidth() / 6);
        int height = (int) (getScreenHeight() / 5);
        Bitmap b = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.arrow), width, height, true);
        arrow.setImageBitmap(b);

        /*if(!locationServiceOff) {
            Location locationView = getLastKnownLocation();
            if (locationView != null) {
                Log.d("Location","Provider " + provider + " has been selected.");
                onLocationChanged(locationView);
            } else {
                Toast.makeText(this, "Location service is not available.", Toast.LENGTH_SHORT).show();
            }
        }*/
        
        //firebase storage
        mStorageRef = FirebaseStorage.getInstance().getReference().child("hi_res_images/background.jpg"); // need exact file name
        getImageFromCloud();
    }

    //get last known locationView using gps or network provider
    private Location getLastKnownLocation() {
        getPermissionFromUser();
        locationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        List<String> providers = locationManager.getProviders(true);
        Location bestLocation = null;
        for (String provider : providers) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return null;
            }
            Location l = locationManager.getLastKnownLocation(provider);
            Log.d("loc",l.toString());
            if (l == null) {
                continue;
            }
            if (bestLocation == null || l.getAccuracy() < bestLocation.getAccuracy()) {
                bestLocation = l;
                this.provider = provider;
                //break;
            }
        }
        return bestLocation;
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
                    //Toast.makeText(MainActivity.this, "Could not download file", Toast.LENGTH_SHORT).show();
                    imageFile = null;
                }
            });
        } catch (IOException ioe) {
        }

        if (imageFile == null) {
            Bitmap bm = BitmapFactory.decodeResource(getResources(), R.drawable.background);
            try {
                imageFile = File.createTempFile("background", "jpg");
                FileOutputStream fos = new FileOutputStream(imageFile);
                bm.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                fos.close();
            } catch (IOException e) {
            }
        }
    }

    //for magnetic field sensor
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

        //degreeView.setText("sensor: "+lux);
        float rotation = (float) ( (azimut * 360 / (2 * Math.PI)) + declination);

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

    //for magnetic field sensor
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (accuracy < 3) {
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

    //for locationView
    @Override
    public void onLocationChanged(Location location) {
        latitude = (float) (location.getLatitude());
        longitude = (float) (location.getLongitude());
        altitude = (float) (location.getAltitude());

        mGeomagneticField = new GeomagneticField(latitude,longitude,altitude,System.currentTimeMillis());
        declination = mGeomagneticField.getDeclination();
        Log.d("loc","declination "+declination);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    //get user permission for locationView access - we need this to get true north
    private void getPermissionFromUser() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d("debug", "not granted");
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                showExplanation("Permission Needed", "Rationale", new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
            } else {
                requestPermission(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
            }
        }
    }

    private void showExplanation(String title,
                                 String message,
                                 final String[] permission,
                                 final int permissionRequestCode) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        requestPermission(permission, permissionRequestCode);
                    }
                });
        builder.create().show();
    }

    private void requestPermission(String[] permissionName, int permissionRequestCode) {
        ActivityCompat.requestPermissions(this, permissionName, permissionRequestCode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    userPermission = true;
                }
            }
        }
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
        //for locationView service
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if(locationManager!=null && provider!=null)
            locationManager.requestLocationUpdates(provider, 400, 1, this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this, mMagnet);
        mSensorManager.unregisterListener(this, mAccelerometer);
        //for locationView service
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if(locationManager!=null)
            locationManager.removeUpdates(this);
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

/*    public void refreshBackground(View view) {
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
                .setMessage("Do you want to set the background image as your phone's homescreen wallpaper?")
                .setPositiveButton("Yes", dialogClickListener)
                .setNegativeButton("Cancel", dialogClickListener)
                .show();
    }

    public void shareImage(View view) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(imageFile));
        Log.d("share",Uri.fromFile(imageFile).toString());
        sendIntent.setType("image*//*");
        startActivity(Intent.createChooser(sendIntent,"Share"));
    }*/


    public void locationServiceSwitch(View view) {
        if(locationServiceOff) {
            LocationManager service = (LocationManager) getSystemService(LOCATION_SERVICE);
            boolean enabled = service
                    .isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || service.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                    || service.isProviderEnabled(LocationManager.PASSIVE_PROVIDER);
            // check if enabled and if not send user to the GSP settings
            // Better solution would be to display a dialog and suggesting to
            // go to the settings
            if (!enabled) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Turn on Location service")
                        .setMessage("Please turn the location service on from settings. Click ok to go to the Settings.")
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                startActivity(intent);
                            }
                        });
                builder.create().show();
            }

            locationServiceOff = false;
            locationView.setImageResource(R.drawable.ic_location_on_black_24px);
            Toast.makeText(this, "Magnetic declination correction is ON.", Toast.LENGTH_SHORT).show();
        } else {
            locationServiceOff = true;
            locationView.setImageResource(R.drawable.ic_location_off_black_24px);
            declination = 0;
            Toast.makeText(this, "Magnetic declination correction is OFF.", Toast.LENGTH_SHORT).show();

        }

        if(!locationServiceOff) {
            Location location = getLastKnownLocation();
            if (location != null) {
                Log.d("Location","Provider " + provider + " has been selected.");
                onLocationChanged(location);
            } else {
                Toast.makeText(this, "Location service is not available.", Toast.LENGTH_SHORT).show();
                locationServiceOff = true;
                locationView.setImageResource(R.drawable.ic_location_off_black_24px);
            }
        }
    }
}
