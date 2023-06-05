package com.example.sinhoalarm;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.ViewGroup;


import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.util.Base64;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageButton;

import java.security.MessageDigest;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import net.daum.mf.map.api.MapPoint;
import net.daum.mf.map.api.MapView;
import net.daum.mf.map.api.MapPOIItem;



public class MainActivity extends AppCompatActivity {

    private ImageButton curLocBtn;
    private MapView mapView;
    private double latitude = 37.494705526855;
    private double longitude = 126.95994559383;

    MapPOIItem curPstnMk = null;
    MapPOIItem[] trafficLight = new MapPOIItem[10];
    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//      getAppKeyHash();

        curLocBtn = (ImageButton)findViewById(R.id.cur_loc);

        final LocationManager lm = (LocationManager)getSystemService(Context.LOCATION_SERVICE);

        if(Build.VERSION.SDK_INT >= 23 &&
                ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(MainActivity.this, new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION}, 0);
        }else{
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, gpsLocationListner);
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 1, gpsLocationListner);
        }

        //음성 서비스 객체 생성
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                tts.setLanguage(Locale.KOREAN);
            }
        });

        mapView = new MapView(this);
        mapView.setPOIItemEventListener(poiItemEventListener);

        mapView.setMapCenterPoint(MapPoint.mapPointWithGeoCoord(latitude, longitude), true);
        mapView.setZoomLevel(1, true);
        mapView.zoomIn(true);
        mapView.zoomOut(true);

        ViewGroup mapViewContainer = (ViewGroup) findViewById(R.id.map_view);
        mapViewContainer.addView(mapView);

        curLocBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mapView.setMapCenterPoint(MapPoint.mapPointWithGeoCoord(latitude, longitude), true);
                mapView.setZoomLevel(1, true);


                if(curPstnMk != null){
                    mapView.removePOIItem(curPstnMk);
                }
                curPstnMk = new MapPOIItem();
                curPstnMk.setItemName("현재 위치");
                curPstnMk.setTag(1);
                curPstnMk.setMarkerType(MapPOIItem.MarkerType.CustomImage);
                curPstnMk.setCustomImageResourceId(R.drawable.man);
                curPstnMk.setCustomImageAutoscale(true);
                curPstnMk.setCustomImageAnchor(0.5f, 0.5f);
                curPstnMk.setMapPoint(MapPoint.mapPointWithGeoCoord(latitude, longitude));
                mapView.addPOIItem(curPstnMk);
            }
        });

        double[][] latlong = new double[10][2]; //신호등 위도 경도
        int[][] grTime = new int[10][2];    //초록불, 빨간불 등화 시간

        //신용산초등학교 앞 신호등
        latlong[0] = new double[]{37.519681, 126.974653};
        grTime[0] = new int[]{30, 70};
        latlong[1] = new double[]{37.5199, 126.97403};
        grTime[1] = grTime[0];
        latlong[2] = new double[]{37.519483, 126.975249};
        grTime[2] = grTime[0];
        latlong[3] = new double[]{37.518892, 126.977205};
        grTime[3] = grTime[0];
        latlong[4] = new double[]{37.518544, 126.978316};
        grTime[4] = new int[]{30, 120};

        //신용산역 주변 신호등

        //현재 자정 기준으로 몇초 흘렀는지 계산
        long curTime = (System.currentTimeMillis()%86400000)/1000;

        for(int i = 0; i < trafficLight.length; i++){
            if(latlong[i][0] == 0.0 || grTime[i][0] == 0)
                break;

            int pastTime = 0;   //현재 기준 불이 바뀌고 지난 시간
            pastTime = (int)(curTime % (grTime[i][0] + grTime[i][1]));

            trafficLight[i] = new MapPOIItem();
            trafficLight[i].setTag(0);
            trafficLight[i].setItemName("신호 상황");
            trafficLight[i].setMarkerType(MapPOIItem.MarkerType.CustomImage);
            if(pastTime > grTime[i][0]){
                trafficLight[i].setCustomImageResourceId(R.drawable.signallight_red);
            }else{
                trafficLight[i].setCustomImageResourceId(R.drawable.signallight_green);
            }
            trafficLight[i].setCustomImageAutoscale(true);
            trafficLight[i].setCustomImageAnchor(0.5f, 0.5f);
            trafficLight[i].setMapPoint(MapPoint.mapPointWithGeoCoord(latlong[i][0], latlong[i][1]));
            mapView.addPOIItem(trafficLight[i]);

            changeLight(trafficLight[i], grTime[i][0], grTime[i][1], pastTime);
        }

    }


    private void changeLight(MapPOIItem item, int green, int red, int pastTime){

        if(item.getCustomImageResourceId() == R.drawable.signallight_green){
            item.setTag(green - pastTime);
        }else{
            item.setTag(green + red - pastTime);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                while(item.getTag() >= 0){
                    try{
                        Thread.sleep(1000);
                        if(item.getTag() == 0){
                            Thread.sleep(1000);
                            mapView.removePOIItem(item);
                            if(item.getCustomImageResourceId() == R.drawable.signallight_green){
                                item.setCustomImageResourceId(R.drawable.signallight_red);
                                item.setTag(red);
                            }else{
                                item.setCustomImageResourceId(R.drawable.signallight_green);
                                item.setTag(green);
                            }
                            mapView.addPOIItem(item);
                        }
                        item.setItemName(item.getTag() + "초");
                        item.setTag(item.getTag() - 1);
                    }catch (InterruptedException e){
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    Timer timer;
    TimerTask timerTask;
    MapView.POIItemEventListener poiItemEventListener = new MapView.POIItemEventListener() {
        @Override
        public void onPOIItemSelected(MapView mapView, MapPOIItem mapPOIItem) {

            if(mapPOIItem.getCustomImageResourceId() == R.drawable.signallight_green){
                tts.speak("파란불" + mapPOIItem.getTag() + "초", TextToSpeech.QUEUE_FLUSH, null);
            }else{
                tts.speak("빨간불" + mapPOIItem.getTag() + "초", TextToSpeech.QUEUE_FLUSH, null);
            }

            if(timerTask != null){
                timerTask.cancel();
            }
            if(timer != null){
                timer.cancel();
            }
            timer = new Timer();
            timerTask = new TimerTask() {
                @Override
                public void run() {
                    if(mapPOIItem.getTag()%5 == 0 && mapPOIItem.getTag() != 0){
                        tts.speak(mapPOIItem.getTag() + "초", TextToSpeech.QUEUE_FLUSH, null);
                    }else if(mapPOIItem.getTag() < 10 && mapPOIItem.getTag() != 0){
                        tts.speak(mapPOIItem.getTag() + "초", TextToSpeech.QUEUE_FLUSH, null);
                    }else if(mapPOIItem.getTag() == 0){
                        if(mapPOIItem.getCustomImageResourceId() == R.drawable.signallight_green){
                            tts.speak("빨간 불로 바뀝니다.", TextToSpeech.QUEUE_ADD, null);
                        }else{
                            tts.speak("파란 불로 바뀝니다.", TextToSpeech.QUEUE_ADD, null);
                        }
                    }

                }
            };
            timer.schedule(timerTask, 0, 1000);

        }

        @Override
        public void onCalloutBalloonOfPOIItemTouched(MapView mapView, MapPOIItem mapPOIItem) {

        }

        @Override
        public void onCalloutBalloonOfPOIItemTouched(MapView mapView, MapPOIItem mapPOIItem, MapPOIItem.CalloutBalloonButtonType calloutBalloonButtonType) {

        }

        @Override
        public void onDraggablePOIItemMoved(MapView mapView, MapPOIItem mapPOIItem, MapPoint mapPoint) {

        }
    };

    final LocationListener gpsLocationListner = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            String provider = location.getProvider();
            latitude = location.getLatitude();
            longitude = location.getLongitude();
            longitude = location.getLongitude();

            if(curPstnMk != null){
                mapView.removePOIItem(curPstnMk);
                curPstnMk.setMapPoint(MapPoint.mapPointWithGeoCoord(latitude, longitude));
                mapView.addPOIItem(curPstnMk);
            }
        }

        public void onStatusChanged(String provider, int status, Bundle extras){}
        public void onProviderEnabled(String provider){}
        public void onProviderDisabled(String provider){}
    };

    private void getAppKeyHash() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES);
            for (Signature signature : info.signatures) {
                MessageDigest md;
                md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                String something = new String(Base64.encode(md.digest(), 0));
                Log.e("Hash key", something);
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            Log.e("name not found", e.toString());
        }
    }

}