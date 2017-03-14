package net.net16.suvankar.compass;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager mSensorManager;
    private Sensor mMagnet;
    private Sensor mAccelerometer;
    private float[] mGravity;
    private float[] mGeomagnetic;

    //UI elements
    private TextView degreeView;
    private ImageView compassView;
    private ImageView arrow;
    private TextView strengthView;

    //to smooth the sensor values
    static final float ALPHA = 0.05f; // if ALPHA = 1 OR 0, no filter applies.

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

        //rescaling the arrow to fit the screen properly
        int width = (int)(getScreenWidth()/6);
        int height = (int)(getScreenHeight()/5);
        Bitmap b = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(),R.drawable.arrow),width,height,true);
        arrow.setImageBitmap(b);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float azimut = 0;
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            mGravity = lowPass(event.values.clone(), mGravity);

        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            mGeomagnetic = lowPass(event.values.clone(), mGeomagnetic);
            float strn = (float) Math.sqrt(mGeomagnetic[0]*mGeomagnetic[0]+mGeomagnetic[1]*mGeomagnetic[1]+mGeomagnetic[2]*mGeomagnetic[2]);
            strengthView.setText((int)strn+" µT");
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
        float rotation = (float)(azimut * 360 / (2 * Math.PI));

        int deg = 0;
        if(rotation>0) {
            deg = (int)rotation;
        } else {
            deg = (int)(360-(rotation*-1));
        }

        if(deg<22 || deg>=338) {
            degreeView.setText("North "+deg+"\u00B0");
        } else if(deg>=22 && deg<67) {
            degreeView.setText("North East "+deg+"\u00B0");
        } else if(deg>=67 && deg<112) {
            degreeView.setText("East "+deg+"\u00B0");
        } else if(deg>=112 && deg<157) {
            degreeView.setText("South East "+deg+"\u00B0");
        } else if(deg>=157 && deg<202) {
            degreeView.setText("South "+deg+"\u00B0");
        } else if(deg>=202 && deg<247) {
            degreeView.setText("South West"+deg+"\u00B0");
        } else if(deg>=247 && deg<292) {
            degreeView.setText("West"+deg+"\u00B0");
        } else if(deg>=292 && deg<338) {
            degreeView.setText("North West"+deg+"\u00B0");
        }

        //rotate the compass image according to the rotation
        compassView.setRotation(-rotation);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if(accuracy<3)
        Toast.makeText(this, "Please calibrate your device!", Toast.LENGTH_SHORT).show();
    }

    // https://en.wikipedia.org/wiki/Low-pass_filter
    // to smooth the sensor values
    protected float[] lowPass( float[] input, float[] output ) {
        if ( output == null ) return input;

        for ( int i=0; i<input.length; i++ ) {
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
        mSensorManager.unregisterListener(this,mMagnet);
        mSensorManager.unregisterListener(this,mAccelerometer);
    }

    public void drawInfoFragment(View view) {
        FragmentManager fm = getSupportFragmentManager();
        fm.beginTransaction().setCustomAnimations(R.anim.frag_enter_from_right, R.anim.frag_exit_to_left,
                R.anim.frag_enter_from_left, R.anim.frag_exit_to_right)
                .addToBackStack(this.getLocalClassName())
                .replace(android.R.id.content,new InfoFragment())
                .commit();
    }
}
