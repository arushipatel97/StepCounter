package com.example.stepcounter;

import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import static java.lang.Float.MAX_VALUE;
import static java.lang.Float.MIN_VALUE;
import static java.lang.Math.abs;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    TextView tv_steps;

    private static final int HISTORY_SIZE = 250;
    XYPlot plot;
    SensorManager sensorManager;

    private float[] gravity = new float[3];
    final float alpha = 0.8f;

    private SimpleXYSeries accelerometerZ = null;
    private LinkedList<Float> accelerometerRaw = new LinkedList<Float>();
    private SimpleXYSeries threshold = null;
    private SimpleXYSeries median = null;

    boolean running = false;

    float avgThreshold;
    private int steps = 0;
    private float min = 0;
    private float max = 0;
    float prevZ = 0;
    private float nextMin = MAX_VALUE;
    private float nextMax = MIN_VALUE;
    private long nextChange = 500;

    private long lastUpdate = 0;

    @Override
    protected  void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tv_steps = (TextView) findViewById(R.id.tv_steps);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);


        plot = (XYPlot) findViewById(R.id.plot);
        accelerometerZ = new SimpleXYSeries("accelerometerZ");
        accelerometerZ.useImplicitXVals();

        plot.setRangeBoundaries(-8, 8, BoundaryMode.FIXED);
        plot.setDomainBoundaries(0, HISTORY_SIZE, BoundaryMode.FIXED);
        threshold = new SimpleXYSeries("threshold");
        threshold.useImplicitXVals();

        median = new SimpleXYSeries("median");
        median.useImplicitXVals();

        plot.addSeries(accelerometerZ,
                new LineAndPointFormatter(
                        Color.rgb(100, 100, 200), null, null, null));
        plot.addSeries(threshold,
                new LineAndPointFormatter(
                        Color.rgb(255, 0, 0), null, null, null));
        plot.addSeries(median,
                new LineAndPointFormatter(
                        Color.rgb(0, 255, 0), null, null, null));
        tv_steps.setText(String.valueOf(steps));
        Button reset = (Button) findViewById(R.id.reset);
        reset.setOnClickListener( new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                steps = 0;
                tv_steps.setText(String.valueOf(steps));
            }
        });

    }

    @Override
    protected void onResume(){
        super.onResume();
        running = true;
        Sensor countSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (countSensor != null){
            sensorManager.registerListener(this, countSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        running = false;
    }

    @Override
    public void onSensorChanged(SensorEvent event){

        if (running && event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            //Accounting for gravity
            gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];
            float z = event.values[2] - gravity[2];

            long curTime = System.currentTimeMillis();
            if ((curTime - lastUpdate) >= 50) {
                if (accelerometerZ.size() > HISTORY_SIZE){
                    accelerometerZ.removeFirst();
                    threshold.removeFirst();
                    accelerometerRaw.removeFirst();
                    median.removeFirst();
                }
                accelerometerZ.addLast(null, z);
                accelerometerRaw.addLast(z);
                threshold.addLast(null, avgThreshold);

                //Median Filtering
                int medianBack = 2;
                if (accelerometerZ.size() < medianBack){
                    medianBack = accelerometerZ.size();
                }
                float zMed = z;
                List<Float> medianSort = new ArrayList<Float>();
                for (int i = accelerometerZ.size() - medianBack; i < accelerometerZ.size(); i++) {
                    float val = (float) accelerometerRaw.get(i);
                    medianSort.add(i - (accelerometerZ.size() - medianBack), val);
                }
                Collections.sort(medianSort);
                int mid = medianSort.size()/2;
                zMed = (medianSort.get(mid));
                median.addLast(null, zMed);
                lastUpdate = curTime;
                if (curTime >= nextChange) {
                    nextChange = curTime + 500;
                    max = nextMax;
                    min = nextMin;
                    nextMax = MIN_VALUE;
                    nextMin = MAX_VALUE;
                }

                //Mean calculation & thresholding
                if (curTime > 500) { //remove gravity
                    avgThreshold = ((min + max) / 2);
                    if (abs(max-min) > 1.5) {
                        if (prevZ > avgThreshold && zMed <= avgThreshold) {
                            steps++;
                            tv_steps.setText(String.valueOf(steps));
                        }
                    }
                }
                if (zMed > nextMax) {
                    nextMax = zMed;
                }
                if (zMed < nextMin) {
                    nextMin = zMed;
                }
                prevZ = zMed;
                plot.redraw();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy){

    }
}