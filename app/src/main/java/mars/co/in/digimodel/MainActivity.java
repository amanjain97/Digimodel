package mars.co.in.digimodel;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;


import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.io.DataOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public class MainActivity extends AppCompatActivity {

    private SensorManager msensorManager;
    private Sensor msensor,msensorg;
    private long lastupdate=0;
    TextView xvalue,yvalue,zvalue,xvaluerot,yvaluerot,zvaluerot;
    double[] gravity,linear_acceleration;
    private static final float NS2S = 1.0f / 1000000000.0f;
    private final float[] deltaRotationVector = new float[4];
    private float timestamp;
    SensorEventListener mAccelerometerSensorListener,mGyroSensorListener;
    Button start,stop;

    double dt;
    int xdisp=0,ydisp=0,zdisp=0;
    DataPoint dp[];
    LineGraphSeries<DataPoint> series;
    GraphView graph;
    ArrayList <DataPoint> arrayList;
    int i=3;
    int counter;
    double acceleration_run_avg[]=new double[3];
    double threshold=1.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        lastupdate=System.currentTimeMillis();
        startActivity(new Intent("android.settings.WIFI_SETTINGS"));
        msensorManager=(SensorManager)getSystemService(Context.SENSOR_SERVICE);
        mapping();
        gravity=new double[3];
        linear_acceleration=new double[3];
        msensor=msensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        msensorg = msensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        msensorManager.registerListener(mGyroSensorListener,msensorg,SensorManager.SENSOR_DELAY_NORMAL);
        msensorManager.registerListener(mAccelerometerSensorListener,msensor,SensorManager.SENSOR_DELAY_NORMAL);

        arrayList= new ArrayList<DataPoint>();
        arrayList.add(new DataPoint(0, 0.1));
        arrayList.add(new DataPoint(1, 0.2));
        arrayList.add(new DataPoint(2,0.1));

        graph = (GraphView) findViewById(R.id.graph);
        series=new LineGraphSeries<DataPoint>();
        graph.addSeries(series);
        // customize a little bit viewport
        Viewport viewport = graph.getViewport();
        viewport.setYAxisBoundsManual(true);
        viewport.setMinY(-10);
        viewport.setMaxY(10);
        viewport.setScrollable(true);



        mAccelerometerSensorListener= new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                Sensor mysensor=event.sensor;
                Log.e("Insidefirst","Accelero");
                if(mysensor.getType()==Sensor.TYPE_ACCELEROMETER){
                    long curtime= System.currentTimeMillis();
                    if((curtime-lastupdate)>10) {
                        dt=curtime-lastupdate;
                        lastupdate = curtime;
                        final double alpha = 0.8;

                        // Isolate the force of gravity with the low-pass filter.
                        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
                        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
                        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

                        // Remove the gravity contribution with the high-pass filter.
                        linear_acceleration[0] = (event.values[0] - gravity[0]);
                        linear_acceleration[1] = (event.values[1] - gravity[1]);
                        linear_acceleration[2] = (event.values[2] - gravity[2]);

                        if(counter!=5){
                            acceleration_run_avg[0]+=linear_acceleration[0];
                            acceleration_run_avg[1]+=linear_acceleration[1];
                            acceleration_run_avg[2]+=linear_acceleration[2];
                            counter++;
                        }
                        else{
                            counter=0;

                            if(acceleration_run_avg[0]>threshold)
                            {
                                xdisp+=1;
                            }
                            else if(acceleration_run_avg[0]<threshold*(-1)){
                                xdisp-=1;
                            }

                            ///////////////////////////////////////////////
                            if(acceleration_run_avg[1]>threshold)
                            {
                                ydisp+=1;
                            }
                            else if(acceleration_run_avg[1]<threshold*(-1)){
                                ydisp-=1;
                            }
                            ///////////////////////////////////////////////
                            if(acceleration_run_avg[2]>threshold)
                            {
                                zdisp+=1;
                            }
                            else if(acceleration_run_avg[2]<threshold*(-1)){
                                zdisp-=1;
                            }
                            ///////////////////////////////////////////////

                            series.appendData(new DataPoint(i++, xdisp), true, 10);

                            acceleration_run_avg[0]=0;
                            acceleration_run_avg[1]=0;
                            acceleration_run_avg[2]=0;
                        }

                        xvalue.setText(xdisp + " ");
                        yvalue.setText(ydisp + " ");
                        zvalue.setText(zdisp + " ");

                        Integer[] acc_co=new Integer[3];
                        acc_co[0]=xdisp;
                        acc_co[1]=ydisp;
                        acc_co[2]=zdisp;
                        new ConnectClientToServer().execute(acc_co);
                    }

                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        };

        mGyroSensorListener=new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                Sensor mysensor=event.sensor;
                Log.e("InsideSecond","Gyro");
                if(mysensor.getType()==Sensor.TYPE_GYROSCOPE){
                    if (timestamp != 0) {
                        final float dT = (event.timestamp - timestamp) * NS2S;
                        // Axis of the rotation sample, not normalized yet.
                        float axisX = event.values[0];
                        float axisY = event.values[1];
                        float axisZ = event.values[2];

                        // Calculate the angular speed of the sample
                        double omegaMagnitude = Math.sqrt(axisX*axisX + axisY*axisY + axisZ*axisZ);

                        // Normalize the rotation vector if it's big enough to get the axis
                        // (that is, EPSILON should represent your maximum allowable margin of error)
                        if (omegaMagnitude > Math.pow(10,-2)) {
                            axisX /= omegaMagnitude;
                            axisY /= omegaMagnitude;
                            axisZ /= omegaMagnitude;
                        }

                        // Integrate around this axis with the angular speed by the timestep
                        // in order to get a delta rotation from this sample over the timestep
                        // We will convert this axis-angle representation of the delta rotation
                        // into a quaternion before turning it into the rotation matrix.
                        double thetaOverTwo = omegaMagnitude * dT / 2.0f;
                        double sinThetaOverTwo = Math.sin(thetaOverTwo);
                        double cosThetaOverTwo = Math.cos(thetaOverTwo);
                        deltaRotationVector[0] = (float) (sinThetaOverTwo * axisX);
                        deltaRotationVector[1] = (float) (sinThetaOverTwo * axisY);
                        deltaRotationVector[2] = (float) (sinThetaOverTwo * axisZ);
                        deltaRotationVector[3] = (float) (cosThetaOverTwo);
                        xvaluerot.setText(axisX+"");
                        yvaluerot.setText(axisY+"");
                        zvaluerot.setText(axisZ+"");
                    }
                    timestamp = event.timestamp;
                    float[] deltaRotationMatrix = new float[9];
                    SensorManager.getRotationMatrixFromVector(deltaRotationMatrix, deltaRotationVector);
                    // User code should concatenate the delta rotation we computed with the current rotation
                    // in order to get the updated rotation.
                    // rotationCurrent = rotationCurrent * deltaRotationMatrix;
                }

            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        };

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
               Toast.makeText(getApplication(),"Sending Started",Toast.LENGTH_LONG).show();

//                wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
//                dhcpInfo = wifiManager.getDhcpInfo();
                new ConnectClientToServer().execute();

            }
        });

        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getApplication(),"Sending Stoped",Toast.LENGTH_LONG).show();
                try {
                    //client.close();
                }
                catch (Exception e){
                    Log.e("stopped",e.toString());
                }
            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }
    @Override
    protected void onDestroy()
    {
        super.onDestroy();
    }

    private void mapping(){
        xvalue=(TextView)findViewById(R.id.xvaluedisplay);
        yvalue=(TextView)findViewById(R.id.yvaluedisplay);
        zvalue=(TextView)findViewById(R.id.zvaluedisplay);
        xvaluerot=(TextView)findViewById(R.id.xvaluerotdisplay);
        yvaluerot=(TextView)findViewById(R.id.yvaluerotdisplay);
        zvaluerot=(TextView)findViewById(R.id.zvaluerotdisplay);
        start=(Button)findViewById(R.id.startsending);
        stop=(Button)findViewById(R.id.stopsending);

    }

    @Override
    protected void onResume() {
        super.onResume();
        msensorManager.registerListener(mAccelerometerSensorListener,msensor,SensorManager.SENSOR_DELAY_NORMAL);
        msensorManager.registerListener(mGyroSensorListener,msensorg,SensorManager.SENSOR_DELAY_NORMAL);

    }

    @Override
    protected void onPause() {
        super.onPause();
        msensorManager.unregisterListener(mAccelerometerSensorListener);
        msensorManager.unregisterListener(mGyroSensorListener);

    }

    class ConnectClientToServer extends AsyncTask<Integer,String,Boolean>{
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Boolean doInBackground(Integer... params) {
            try {

                List<NameValuePair> coordi = new ArrayList<NameValuePair>();

                coordi.add(new BasicNameValuePair("x", params[0]+""));
                coordi.add(new BasicNameValuePair("y", params[1]+""));
                coordi.add(new BasicNameValuePair("z", params[2]+""));

                ServiceHandler serviceClient = new ServiceHandler();

                String json = serviceClient.makeServiceCall("http://192.168.150.4:8000/",
                        ServiceHandler.POST, coordi);
                Log.i("Response from server",json.toString());

            }
            catch (Exception e){
                Log.e("Connecting to server",e.toString());
            }
            return null;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);
        }
    }
}
