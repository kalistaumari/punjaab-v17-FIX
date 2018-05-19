/*
** Created by Andina Zahra Nabilla on 10 April 2018
** Created by Kalista Umari on 30 April 2018
*
* Activity berfungsi untuk:
* 1. Inisialisasi sensor awal
* 2. Deteksi data yang dibutuhkan melalui sensor (Accelerometer dan Heartrate)
* 3. Melakukan processing fall detection menggunakan data accelerometer yang sudah didapatkan
* 4. Mengirimkan data yang sudah didapatkan ke MainActivity menggunakan Intent Broadcast
* 5. Mengirimkan data yang sudah didapatkan melalui Device Client ke Smartphone menggunakan perintah client send sensor data (heartrate dalam bentuk sensor awal dan accelerometer dalam bentuk state)
* 6. Inisialisasi Client
* 7. Fungsi Activity Recognition
* 8. Pemanggilan fungsi activity recognition
*
* To be fixed:
* - Delay State Activity Recognition di Wear (seconds) = 36 35 34
* - Delay State Activity Recognition di Wear (value) = 1109 1114 1146
* - Delay processing fall detection di Wear (seconds) = 10 7 8 7 4 5 7 8 9 6
* - Dalam 1 menit:
*   Accelerometer Wear (value) = 2338 2726 2585 2611 2570 2586 2638 2513 2441 2396
*   Accelerometer HP (value) = 44 43 43 43 43 43 44 43 43 43
*   HR Wear (value) = 40 38 37 41 34 38 35 38 38 40
*   HR HP (value) = 26 26 24 26 26 24 26 26 26 26
* - Fall Range di Wear(value) = 1 - 3
* - Heart Abnormality Range di Wear(value) = 7 9 6 5 5
 */

package com.andinazn.sensordetectionv2;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SensorService extends Service implements SensorEventListener {
    private static final String TAG = "SensorDashboard/SensorService";

    private final static int SENS_ACCELEROMETER = Sensor.TYPE_ACCELEROMETER;
    private final static int SENS_HEARTRATE = Sensor.TYPE_HEART_RATE;

    SensorManager mSensorManager;

    private Sensor mHeartrateSensor;
    ScheduledExecutorService hrScheduler;
    private DeviceClient client;
    Notification.Builder builder;

    float gravity[] = {0f, 0f, 0f}, linear_acceleration[] = {0f, 0f, 0f}; //0f = float 0
    double Zvalue, totLinear, totAcc, FallCounter = 0, threshold = 38, g = 9.8;
    double Aj, Ajtot, Mu, Sigma, AI, AItot = 0;
    double N, k = 0;
    int stateactivity = 0;


    List<Double> ajList;
    //List<Double> subAjList;
    List<Double> sigmaList;
    //List<Double> subSigmaList;

    boolean flag = false;

    Handler handler;
    int delay = 250;

    private float tmpHR = 0;

    @Override
    public void onCreate() {
        super.onCreate();

        //6. Inisialisasi Client
        client = DeviceClient.getInstance(this);

        builder = new Notification.Builder(this);
        builder.setContentTitle("Fall Detection");
        builder.setContentText("Collecting heartrate and acceleration sensor data..");
        builder.setSmallIcon(R.drawable.ic_launcher);

        startForeground(1, builder.build());

        startMeasurement();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        stopMeasurement();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    protected void startMeasurement() {

        //1. Inisialisasi sensor awal
        mSensorManager = ((SensorManager) getSystemService(SENSOR_SERVICE));

        Sensor accelerometerSensor = mSensorManager.getDefaultSensor(SENS_ACCELEROMETER);
        mHeartrateSensor = mSensorManager.getDefaultSensor(SENS_HEARTRATE);

        //Register Listener
        if (mSensorManager != null) {
            //Accelerometer Data
            if (accelerometerSensor != null) {
                mSensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                Log.w(TAG, "No Accelerometer found");
            }
            //Heartrate Data
            if (mHeartrateSensor != null) {
                final int measurementDuration   = 30;   // Seconds
                final int measurementBreak      = 15;    // Seconds

                hrScheduler = Executors.newScheduledThreadPool(1);
                hrScheduler.scheduleAtFixedRate(
                        new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG, "register Heartrate Sensor");
                                mSensorManager.registerListener(SensorService.this, mHeartrateSensor, SensorManager.SENSOR_DELAY_NORMAL);

                                try {
                                    Thread.sleep(measurementDuration * 1000);
                                } catch (InterruptedException e) {
                                    Log.e(TAG, "Interrupted while waitting to unregister Heartrate Sensor");
                                }

                                Log.d(TAG, "unregister Heartrate Sensor");
                                mSensorManager.unregisterListener(SensorService.this, mHeartrateSensor);
                            }
                        }, 3, measurementDuration + measurementBreak, TimeUnit.SECONDS);
            } else {
                Log.d(TAG, "No Heartrate Sensor found");
            }

        }

        //8. Pemanggilan fungsi activity recognition

        ajList = new ArrayList<>();
        //subAjList = new ArrayList<>();
        sigmaList = new ArrayList<>();
        //subSigmaList = new ArrayList<>();


        handler = new Handler();
        handler.postDelayed(new Runnable(){
            public void run(){
                activityRecognition();
                //stateactivity = activityRecognition();
                //Log.i("State Activity", "activity recognition = " + stateactivity);
                handler.postDelayed(this, delay);
            }
        }, delay);

    }

    private void stopMeasurement() {
        if (mSensorManager != null)
            mSensorManager.unregisterListener(this);
        if (hrScheduler != null)
            hrScheduler.shutdown();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        //2. Deteksi data yang dibutuhkan melalui sensor (Accelerometer dan Heartrate)
        //Heartrate Data
        if (event.sensor.getType() == SENS_HEARTRATE) {
            tmpHR = event.values[0];

            Log.d(TAG,"Broadcast HR.");
            Log.d("Sensor Detecting HR: ", event.accuracy + "," + event.timestamp + "," + String.valueOf(tmpHR));

            //4. Mengirimkan data yang sudah didapatkan ke MainActivity menggunakan Intent Broadcast
            Intent intent = new Intent();
            intent.setAction("com.example.Broadcast");
            intent.putExtra("HR", event.values);
            intent.putExtra("ACCR", event.accuracy);
            intent.putExtra("TIME", event.timestamp);
            sendBroadcast(intent);
        }

        //Accelerometer Data
        if (event.sensor.getType() == SENS_ACCELEROMETER) {
            //3. Melakukan processing fall detection menggunakan data accelerometer yang sudah didapatkan
            final float alpha = (float) 0.8;
            // Isolate the force of gravity with the low-pass filter.
            gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]; // gravity = 0.8 * gravity[0] + (1-0.8) * acceleration
            gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
            gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

            // Remove the gravity contribution with the high-pass filter.
            linear_acceleration[0] = event.values[0] - gravity[0];
            linear_acceleration[1] = event.values[1] - gravity[1];
            linear_acceleration[2] = event.values[2] - gravity[2];

            Log.i("Fall", " gravity 0:" + gravity[0] + " gravity 1: " + gravity[1] + " gravity 2: " + gravity[2]);
            Log.i("Fall", " acc 0:" + event.values[0] + " gravity 1: " + event.values[1] + " gravity 2: " + event.values[2]);
            Log.i("Fall", " linear 0:" + linear_acceleration[0] + " linear 1: " + linear_acceleration[1] + " linear 2: " + linear_acceleration[2]);

            totAcc = Math.sqrt(event.values[0] * event.values[0] +
                    event.values[1] * event.values[1] +
                    event.values[2] * event.values[2]);

            Log.i("Fall", "totAcc = " + totAcc);

            totLinear = Math.sqrt(linear_acceleration[0] * linear_acceleration[0] +
                    linear_acceleration[1] * linear_acceleration[1] +
                    linear_acceleration[2] * linear_acceleration[2]);

            Log.i("Fall", "totLinear = " + totLinear);
            Zvalue = ((totAcc * totAcc) - (totLinear * totLinear) - (g * g))/(2 *g);

            Log.i("Fall", "Z value = " + Zvalue);
            float currentacc [] = {linear_acceleration[0], linear_acceleration[1], linear_acceleration[2]};

            Log.d(TAG,"Broadcast ACC.");
            Log.d("Sensor Detecting Accelerometer: ", event.accuracy + "," + event.timestamp + "," + Arrays.toString(currentacc));

            FallCounter = ((Zvalue > threshold) ? FallCounter + 1 : 0); //if fall counter = totacc > threshold, fallcounter = +1, else 0.

            if (Zvalue > threshold) {
                Log.i("Fall", "melebihi threshold");
            }

            Log.i("Fall", "fall counter = " + FallCounter);

            Log.i("State Activity", "state activity = " + stateactivity);

            if (FallCounter == 3) { //if (FallCounter == 5 && !detected)
                Log.i("Fall", "FALL DETECTED");

                float after[] = {event.values[0], event.values[1], event.values[2]};
                Log.d("Sensor activity: ", "Sensor fall detected on activity recognition: " + event.accuracy + "," + event.timestamp + "," + Arrays.toString(after));


            } else {

                //Asumsi inisialisasi stateactivity resting
                if (stateactivity == 1) {
                    event.values[0] = 1000;
                    event.values[1] = 1000;
                    event.values[2] = 1000;
                }

                if (stateactivity == 2) {
                    event.values[0] = 2000;
                    event.values[1] = 2000;
                    event.values[2] = 2000;
                }

                if (stateactivity == 3) {
                    event.values[0] = 3000;
                    event.values[1] = 3000;
                    event.values[2] = 3000;
                }

                if (stateactivity == 0) {
                    event.values[0] = 5000;
                    event.values[1] = 5000;
                    event.values[2] = 5000;
                }

                float after [] = {event.values[0], event.values[1], event.values[2]};
                Log.d("Sensor activity: ", "Sensor based on activity recognition: " + event.accuracy + "," + event.timestamp + "," + Arrays.toString(after));

            }


        }

        // 5. Mengirimkan data yang sudah didapatkan melalui Device Client ke Smartphone menggunakan perintah client send sensor data (heartrate dalam bentuk sensor awal dan accelerometer dalam bentuk state)
        client.sendSensorData(event.sensor.getType(), event.accuracy, event.timestamp, event.values);

    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    //7. Fungsi Activity Recognition
    public void activityRecognition() {
        if (N<100.0) { //N<250

            N = N+1;
            //Log.i("AI", "bikin N value, N " + N);
            Aj = totAcc;
            ajList.add(Aj);
            //Log.i("AI", "bikin Aj value, Aj " + Aj);

            Ajtot = Ajtot + Aj;


            //Log.i("AI", "bikin Ajtot value, Ajtot " + Ajtot);

            Mu = (1/N)*Ajtot;

            //Log.i("AI", "bikin Mu value, Mu " + Mu);

            Sigma = Math.sqrt((1/N) * ((Aj - Mu) * (Aj - Mu)));
            //Log.i("AI", "bikin Sigma value, Sigma N " + N + " , " + Sigma);


            if (N == 100.0) {


                AI = AI + Sigma;
                // Log.i("AI", "bikin AI value 1 " + AI);
                k = k + 1;
                sigmaList.add(Sigma);

                N = 101.0;

            }
        }

        else if (N == 101.0)
        {
            Aj = totAcc;

            ajList.add(Aj);
            Ajtot = Ajtot + Aj - ajList.get(0);
            //Log.i("AI", "ajlist " + (ajList.size()));
            //ajList = ajList.subList(1, (ajList.size()));
            ajList.remove(0);
            //Log.i("AI", "ajlist baru " + (ajList.size()));

            //Log.i("AI", "bikin Ajtot value 251, Ajtot " + Ajtot);

            Mu = Ajtot / 100.0;

            //Log.i("AI", "bikin Mu value, Mu " + Mu);

            Sigma = Math.sqrt(((Aj - Mu) * (Aj - Mu))/100.0);

            //Log.i("AI", "bikin Sigma value 251, Sigma " + Sigma);




            if (k<12){
                k = k + 1;
                AI = AI + Sigma;
                // Log.i("AI", "bikin AI value, AI k " + k + " , " + AI);
                sigmaList.add(Sigma);
                if(k==12){
                    AItot = AI;
                    // Log.i("AI", "bikin AItot value, AItot 1" + AItot);
                    k = 13;
                }
            }

            else if (k==13){
                sigmaList.add(Sigma);
                //Log.i("AI", "sigmalist " + (sigmaList.size()));
                AI = AI + Sigma - sigmaList.get(0);
                sigmaList.remove(0);
                //Log.i("AI", "sigmalist baru" + (sigmaList.size()));

                AItot = AI;
                //  Log.i("AI", "bikin AItot value, AItot seterus" + AItot);

            }


        }

        if ((AItot != 0) && (AItot < 0.50))
        {
            stateactivity = 1;
            Log.i("AI Activity conclusion", "Resting, AItot " + AItot + "State: " + stateactivity);
        }

        if ((AItot != 0)  && (AItot >= 0.50) && (AItot < 4.0))
        {
            stateactivity = 2;
            Log.i("AI Activity conclusion", "Moderate Activity, AItot " + AItot + "State: " + stateactivity);
        }
        if ((AItot != 0)  && (AItot >= 4.00))
        {
            stateactivity = 3;
            Log.i("AI Activity conclusion", "Vigorous Activity, AItot " + AItot + "State: " + stateactivity);
        }

        //return stateactivity;

        /* if Anything, heart rate < 60 = abnormal
         if Resting, heart rate > 100 = abnormal
         if light to moderate activity, hr > 220-age*07 = take a rest
         if vigorous activity, hr > 220-age*0,85 = take a rest*/
    }

}
