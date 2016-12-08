package com.chris.demonavi;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.search.core.RouteLine;
import com.baidu.mapapi.search.core.SearchResult;
import com.baidu.mapapi.search.route.BikingRouteResult;
import com.baidu.mapapi.search.route.DrivingRouteLine;
import com.baidu.mapapi.search.route.DrivingRoutePlanOption;
import com.baidu.mapapi.search.route.DrivingRouteResult;
import com.baidu.mapapi.search.route.IndoorRouteResult;
import com.baidu.mapapi.search.route.MassTransitRouteLine;
import com.baidu.mapapi.search.route.MassTransitRouteResult;
import com.baidu.mapapi.search.route.OnGetRoutePlanResultListener;
import com.baidu.mapapi.search.route.PlanNode;
import com.baidu.mapapi.search.route.RoutePlanSearch;
import com.baidu.mapapi.search.route.TransitRouteResult;
import com.baidu.mapapi.search.route.WalkingRouteResult;
import com.baidu.navisdk.adapter.BNOuterLogUtil;
import com.baidu.navisdk.adapter.BNOuterTTSPlayerCallback;
import com.baidu.navisdk.adapter.BNRoutePlanNode;
import com.baidu.navisdk.adapter.BNaviSettingManager;
import com.baidu.navisdk.adapter.BaiduNaviManager;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;


public class NaviActivity extends Activity {

    public static final LatLng xxsdLatLng = new LatLng(30.2743630000,120.0740200000);//西溪湿地中心经纬度
    public static final LatLng zjcLatLng = new LatLng(30.2597190000,120.0676780000);//周家村经纬度
    public static final LatLng xxttLatLng = new LatLng(30.2709950000,120.0973130000);//西溪天堂

    // 定位相关
    LatLng mCurrentLocation = new LatLng(0,0);//LatLng(double latitude, double longitude)
    LocationClient mLocClient;
    public MyLocationListenner myListener;
    private MyLocationConfiguration.LocationMode mCurrentMode;
    BitmapDescriptor mCurrentMarker;
    MapView mMapView;
    BaiduMap mBaiduMap;
    boolean isFirstLoc = true; // 是否首次定位

    // 浏览路线节点相关
    Button mBtnPre = null; // 上一个节点
    Button mBtnNext = null; // 下一个节点
    int nodeIndex = -1; // 节点索引,供浏览节点时使用
    RouteLine route = null;
    DrivingRouteLine route_zjc = null;
    DrivingRouteLine route_xxtt = null;
    MassTransitRouteLine massroute = null;
    OverlayManager routeOverlay = null;
    boolean useDefaultIcon = false;
    private TextView popupText = null; // 泡泡view


    // 搜索相关
    RoutePlanSearch mSearch = null;    // 搜索模块，也可去掉地图模块独立使用
    WalkingRouteResult nowResultwalk = null;
    BikingRouteResult nowResultbike = null;
    TransitRouteResult nowResultransit = null;
    DrivingRouteResult nowResultdrive  = null;
    MassTransitRouteResult nowResultmass = null;

    int nowSearchType = -1 ; // 当前进行的检索，供判断浏览节点时结果使用。

    private TextView tv_desc;



    public static List<Activity> activityList = new LinkedList<Activity>();

    private static final String APP_FOLDER_NAME = "BNSDKSimpleDemo";

    private Button mDb06ll = null;
    private String mSDCardPath = null;

    public static final String ROUTE_PLAN_NODE = "routePlanNode";
    public static final String SHOW_CUSTOM_ITEM = "showCustomItem";
    public static final String RESET_END_NODE = "resetEndNode";
    public static final String VOID_MODE = "voidMode";
    BNRoutePlanNode sNode = null;
    BNRoutePlanNode eNode,zjcNode = new BNRoutePlanNode(120.0676780000,30.2597190000,"周家村",null,BNRoutePlanNode.CoordinateType.BD09LL);
    BNRoutePlanNode xxttNode = new BNRoutePlanNode(120.0973130000,30.2709950000, "西溪天堂", null, BNRoutePlanNode.CoordinateType.BD09LL);
    @Override
    public void onCreate(Bundle savedInstanceState) {
        SDKInitializer.initialize(getApplicationContext());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navi);
        // 地图初始化
        mMapView = (MapView) findViewById(R.id.bmapView);
        mBaiduMap = mMapView.getMap();
        mCurrentMode = MyLocationConfiguration.LocationMode.NORMAL;
        mBaiduMap.setMyLocationConfigeration(new MyLocationConfiguration(
                        mCurrentMode, true, mCurrentMarker));

        locate();
        zoom();
        findViewById(R.id.zhoujiacun).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(route_zjc!=null){
                    drawDrivingRoute(route_zjc);
                }else {
                    locateAndDrawRoute(zjcLatLng);
                }
                eNode = zjcNode;
            }
        });
        findViewById(R.id.xixitiantang).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(route_zjc!=null){
                    drawDrivingRoute(route_xxtt);
                }else {
                    locateAndDrawRoute(xxttLatLng);
                }
                eNode = xxttNode;
            }
        });
        tv_desc = (TextView) findViewById(R.id.tv_desc);


        activityList.add(this);


        mDb06ll = (Button) findViewById(R.id.navi);
        // 打开log开关
        BNOuterLogUtil.setLogSwitcher(true);

        initListener();
        if (initDirs()) {
            initNavi();
        }
    }
    private void locate(){
        // 开启定位图层
        mBaiduMap.setMyLocationEnabled(true);
        // 定位初始化
        mLocClient = new LocationClient(this);
        myListener = new MyLocationListenner() {
            @Override
            public void handleResult() {
                search(mCurrentLocation,zjcLatLng);
                search(mCurrentLocation,xxttLatLng);
            }
        };
        mLocClient.registerLocationListener(myListener);
        LocationClientOption option = new LocationClientOption();
        option.setOpenGps(true); // 打开gps
        option.setCoorType("bd09ll"); // 设置坐标类型
        option.setScanSpan(1000);//1秒定位一次
        mLocClient.setLocOption(option);
        mLocClient.start();
    }
    private void locateAndDrawRoute(LatLng latLng){
        if(latLng == zjcLatLng){
            myListener = new MyLocationListenner() {
                @Override
                public void handleResult() {
                    searchAndDrawRoute(mCurrentLocation,zjcLatLng);
                }
            };
        }else if (latLng == xxttLatLng){
            myListener = new MyLocationListenner() {
                @Override
                public void handleResult() {
                    searchAndDrawRoute(mCurrentLocation,xxttLatLng);
                }
            };
        }else {
            myListener = new MyLocationListenner() {
                @Override
                public void handleResult() {
                    search(mCurrentLocation,zjcLatLng);
                    search(mCurrentLocation,xxttLatLng);
                }
            };
        }
        locate();
    }
    private void zoom(){
        MapStatus.Builder builder = new MapStatus.Builder();
        builder.target(xxsdLatLng).zoom(15.0f);
        mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()));
    }
    /**
     * 定位SDK监听函数
     */
    public abstract class MyLocationListenner implements BDLocationListener {

        @Override
        public void onReceiveLocation(BDLocation location) {
            // map view 销毁后不在处理新接收的位置
            if (location == null || mMapView == null) {
                return;
            }
            MyLocationData locData = new MyLocationData.Builder()
                    .accuracy(location.getRadius())
                    // 此处设置开发者获取到的方向信息，顺时针0-360
                    .direction(location.getDirection())//GPS定位时方向角度
                    .latitude(location.getLatitude())
                    .longitude(location.getLongitude()).build();
            mBaiduMap.setMyLocationData(locData);
            mCurrentLocation = new LatLng(location.getLatitude(),location.getLongitude());
            if (isFirstLoc) {
                isFirstLoc = false;
                handleResult();
                sNode = new BNRoutePlanNode(location.getLongitude(),location.getLatitude(),"我的位置",null,BNRoutePlanNode.CoordinateType.BD09LL);
            }

        }
        public abstract void handleResult();
        public void onReceivePoi(BDLocation poiLocation) {
        }
    }

    private void search(LatLng stLatLng, final LatLng enLatLng) {
        mSearch = RoutePlanSearch.newInstance();
        mSearch.drivingSearch((new DrivingRoutePlanOption())
                .policy(DrivingRoutePlanOption.DrivingPolicy.ECAR_DIS_FIRST)//按路径最短来规划路线
                .from(PlanNode.withLocation(stLatLng))
                .to(PlanNode.withLocation(enLatLng)));
        mSearch.setOnGetRoutePlanResultListener(new OnGetRoutePlanResultListener() {
            @Override
            public void onGetWalkingRouteResult(WalkingRouteResult walkingRouteResult) {

            }

            @Override
            public void onGetTransitRouteResult(TransitRouteResult transitRouteResult) {

            }

            @Override
            public void onGetMassTransitRouteResult(MassTransitRouteResult massTransitRouteResult) {

            }

            @Override
            public void onGetDrivingRouteResult(DrivingRouteResult result) {
                if (result == null || result.error != SearchResult.ERRORNO.NO_ERROR) {
                    Toast.makeText(NaviActivity.this, "抱歉，未找到结果", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (result.error == SearchResult.ERRORNO.NO_ERROR) {
                    if (result.getRouteLines().size() > 0 ) {
                        if(enLatLng==zjcLatLng){
                            route_zjc = result.getRouteLines().get(0);//取第一条路线
                        }else if(enLatLng == xxttLatLng) {
                            route_xxtt = result.getRouteLines().get(0);
                        }else {
                            nowResultdrive = result;
                            route = result.getRouteLines().get(0);//取第一条路线
                            DrivingRouteOverlay overlay = new DrivingRouteOverlay(mBaiduMap);
                            mBaiduMap.setOnMarkerClickListener(overlay);
                            routeOverlay = overlay;
                            overlay.setData(nowResultdrive.getRouteLines().get(0));
                            overlay.addToMap();
                            overlay.zoomToSpan();
                        }
                    } else {
                        Log.d("route result", "结果数<0" );
                        return;
                    }
                }
            }

            @Override
            public void onGetIndoorRouteResult(IndoorRouteResult indoorRouteResult) {

            }

            @Override
            public void onGetBikingRouteResult(BikingRouteResult bikingRouteResult) {

            }
        });
    }
    public void drawDrivingRoute(DrivingRouteLine route){
        DrivingRouteOverlay overlay = new DrivingRouteOverlay(mBaiduMap);
        mBaiduMap.setOnMarkerClickListener(overlay);
        mBaiduMap.clear();
        routeOverlay = overlay;
        overlay.setData(route);
        overlay.addToMap();
        overlay.zoomToSpan();

        int time = route.getDuration();
        int distance = route.getDistance();
        if (time / 3600 == 0) {
            tv_desc.setText("全程约"+distance+"米,乘车大约需要：" + time / 60 + "分钟");
        } else {
            tv_desc.setText("全程约"+distance+"米,乘车大约需要：" + time / 3600 + "小时" + (time % 3600) / 60 + "分钟");
        }
    }
    public void searchAndDrawRoute(LatLng stLatLng, final LatLng enLatLng){
        mSearch = RoutePlanSearch.newInstance();
        mSearch.drivingSearch((new DrivingRoutePlanOption())
                .policy(DrivingRoutePlanOption.DrivingPolicy.ECAR_DIS_FIRST)//按路径最短来规划路线
                .from(PlanNode.withLocation(stLatLng))
                .to(PlanNode.withLocation(enLatLng)));
        mSearch.setOnGetRoutePlanResultListener(new OnGetRoutePlanResultListener() {
            @Override
            public void onGetWalkingRouteResult(WalkingRouteResult walkingRouteResult) {

            }

            @Override
            public void onGetTransitRouteResult(TransitRouteResult transitRouteResult) {

            }

            @Override
            public void onGetMassTransitRouteResult(MassTransitRouteResult massTransitRouteResult) {

            }

            @Override
            public void onGetDrivingRouteResult(DrivingRouteResult result) {
                if (result == null || result.error != SearchResult.ERRORNO.NO_ERROR) {
                    Toast.makeText(NaviActivity.this, "抱歉，未找到结果", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (result.error == SearchResult.ERRORNO.NO_ERROR) {
                    if (result.getRouteLines().size() > 0 ) {
                        if(enLatLng==zjcLatLng){
                            nowResultdrive = result;
                            route_zjc = result.getRouteLines().get(0);//取第一条路线
                        }else if(enLatLng == xxttLatLng) {
                            nowResultdrive = result;
                            route_xxtt = result.getRouteLines().get(0);
                        }else {
                            nowResultdrive = result;
                            route = result.getRouteLines().get(0);//取第一条路线
                        }
                        DrivingRouteOverlay overlay = new DrivingRouteOverlay(mBaiduMap);
                        mBaiduMap.setOnMarkerClickListener(overlay);
                        mBaiduMap.clear();
                        routeOverlay = overlay;
                        overlay.setData(nowResultdrive.getRouteLines().get(0));
                        overlay.addToMap();
                        overlay.zoomToSpan();
                        int time = route.getDuration();
                        int distance = route.getDistance();
                        if (time / 3600 == 0) {
                            tv_desc.setText("全程约"+distance+"米,乘车大约需要：" + time / 60 + "分钟");
                        } else {
                            tv_desc.setText("全程约"+distance+"米,乘车大约需要：" + time / 3600 + "小时" + (time % 3600) / 60 + "分钟");
                        }

                    } else {
                        Log.d("route result", "结果数<0" );
                        return;
                    }
                }
            }

            @Override
            public void onGetIndoorRouteResult(IndoorRouteResult indoorRouteResult) {

            }

            @Override
            public void onGetBikingRouteResult(BikingRouteResult bikingRouteResult) {

            }
        });
    }
    // 定制RouteOverly
    private class MyDrivingRouteOverlay extends DrivingRouteOverlay {

        public MyDrivingRouteOverlay(BaiduMap baiduMap) {
            super(baiduMap);
        }

        @Override
        public BitmapDescriptor getStartMarker() {

            return null;
        }

        @Override
        public BitmapDescriptor getTerminalMarker() {

            return null;
        }
    }

    @Override
    protected void onPause() {
        mMapView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        mMapView.onResume();
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        // 退出时销毁定位
        mLocClient.stop();
        // 关闭定位图层
        mBaiduMap.setMyLocationEnabled(false);
        mMapView.onDestroy();
        mMapView = null;
        super.onDestroy();
    }

    private void initListener() {

//

        if (mDb06ll != null) {
            mDb06ll.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    if (BaiduNaviManager.isNaviInited()) {
                        routeplanToNavi(BNRoutePlanNode.CoordinateType.BD09LL);
                    }
                }
            });
        }


    }


    private boolean initDirs() {
        mSDCardPath = getSdcardDir();
        if (mSDCardPath == null) {
            return false;
        }
        File f = new File(mSDCardPath, APP_FOLDER_NAME);
        if (!f.exists()) {
            try {
                f.mkdir();
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    String authinfo = null;

    /**
     * 内部TTS播报状态回传handler
     */
    private Handler ttsHandler = new Handler() {
        public void handleMessage(Message msg) {
            int type = msg.what;
            switch (type) {
                case BaiduNaviManager.TTSPlayMsgType.PLAY_START_MSG: {
                    showToastMsg("Handler : TTS play start");
                    break;
                }
                case BaiduNaviManager.TTSPlayMsgType.PLAY_END_MSG: {
                    showToastMsg("Handler : TTS play end");
                    break;
                }
                default :
                    break;
            }
        }
    };

    /**
     * 内部TTS播报状态回调接口
     */
    private BaiduNaviManager.TTSPlayStateListener ttsPlayStateListener = new BaiduNaviManager.TTSPlayStateListener() {

        @Override
        public void playEnd() {
            showToastMsg("TTSPlayStateListener : TTS play end");
        }

        @Override
        public void playStart() {
            showToastMsg("TTSPlayStateListener : TTS play start");
        }
    };

    public void showToastMsg(final String msg) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(NaviActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initNavi() {

        BNOuterTTSPlayerCallback ttsCallback = null;

        BaiduNaviManager.getInstance().init(this, mSDCardPath, APP_FOLDER_NAME, new BaiduNaviManager.NaviInitListener() {
            @Override
            public void onAuthResult(int status, String msg) {
                if (0 == status) {
                    authinfo = "key校验成功!";
                } else {
                    authinfo = "key校验失败, " + msg;
                }
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(NaviActivity.this, authinfo, Toast.LENGTH_LONG).show();
                    }
                });
            }

            public void initSuccess() {
                Toast.makeText(NaviActivity.this, "百度导航引擎初始化成功", Toast.LENGTH_SHORT).show();
                initSetting();
            }

            public void initStart() {
                Toast.makeText(NaviActivity.this, "百度导航引擎初始化开始", Toast.LENGTH_SHORT).show();
            }

            public void initFailed() {
                Toast.makeText(NaviActivity.this, "百度导航引擎初始化失败", Toast.LENGTH_SHORT).show();
            }


        },  null, ttsHandler, ttsPlayStateListener);

    }

    private String getSdcardDir() {
        if (Environment.getExternalStorageState().equalsIgnoreCase(Environment.MEDIA_MOUNTED)) {
            return Environment.getExternalStorageDirectory().toString();
        }
        return null;
    }

    private void routeplanToNavi(BNRoutePlanNode.CoordinateType coType) {

//        switch (coType) {
////			case GCJ02: {
////				sNode = new BNRoutePlanNode(116.30142, 40.05087, "百度大厦", null, coType);
////				eNode = new BNRoutePlanNode(116.39750, 39.90882, "北京天安门", null, coType);
////				break;
////			}
////			case WGS84: {
////				sNode = new BNRoutePlanNode(116.300821, 40.050969, "百度大厦", null, coType);
////				eNode = new BNRoutePlanNode(116.397491, 39.908749, "北京天安门", null, coType);
////				break;
////			}
////			case BD09_MC: {
////				sNode = new BNRoutePlanNode(12947471, 4846474, "百度大厦", null, coType);
////				eNode = new BNRoutePlanNode(12958160, 4825947, "北京天安门", null, coType);
////				break;
////			}
//            case BD09LL: {
////				public static final LatLng xxsdLatLng = new LatLng(30.2743630000,120.0740200000);//西溪湿地中心经纬度
////				public static final LatLng zjcLatLng = new LatLng(30.2597190000,120.0676780000);//周家村经纬度
////				public static final LatLng xxttLatLng = new LatLng(30.2709950000,120.0973130000);//西溪天堂
//                sNode = new BNRoutePlanNode(120.0676780000,30.2597190000,"周家村",null,coType);
//                eNode = new BNRoutePlanNode(120.0973130000,30.2709950000, "西溪天堂", null, coType);
//
//                break;
//            }
//            default:
//                ;
//        }
        if (sNode != null && eNode != null) {
            List<BNRoutePlanNode> list = new ArrayList<BNRoutePlanNode>();
            list.add(sNode);
            list.add(eNode);
            BaiduNaviManager.getInstance().launchNavigator(this, list, 1, false, new DemoRoutePlanListener(sNode));
        }
    }

    public class DemoRoutePlanListener implements BaiduNaviManager.RoutePlanListener {

        private BNRoutePlanNode mBNRoutePlanNode = null;

        public DemoRoutePlanListener(BNRoutePlanNode node) {
            mBNRoutePlanNode = node;
        }

        @Override
        public void onJumpToNavigator() {
			/*
			 * 设置途径点以及resetEndNode会回调该接口
			 */

            for (Activity ac : activityList) {

                if (ac.getClass().getName().endsWith("BNDemoGuideActivity")) {

                    return;
                }
            }
            Intent intent = new Intent(NaviActivity.this, BNDemoGuideActivity.class);
            Bundle bundle = new Bundle();
            bundle.putSerializable(ROUTE_PLAN_NODE, (BNRoutePlanNode) mBNRoutePlanNode);
            intent.putExtras(bundle);
            startActivity(intent);

        }

        @Override
        public void onRoutePlanFailed() {
            // TODO Auto-generated method stub
            Toast.makeText(NaviActivity.this, "算路失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void initSetting(){
        // 设置是否双屏显示
        BNaviSettingManager.setShowTotalRoadConditionBar(BNaviSettingManager.PreViewRoadCondition.ROAD_CONDITION_BAR_SHOW_ON);
        // 设置导航播报模式
        BNaviSettingManager.setVoiceMode(BNaviSettingManager.VoiceMode.Veteran);
        // 是否开启路况
        BNaviSettingManager.setRealRoadCondition(BNaviSettingManager.RealRoadCondition.NAVI_ITS_ON);
    }

    private BNOuterTTSPlayerCallback mTTSCallback = new BNOuterTTSPlayerCallback() {

        @Override
        public void stopTTS() {
            // TODO Auto-generated method stub
            Log.e("test_TTS", "stopTTS");
        }

        @Override
        public void resumeTTS() {
            // TODO Auto-generated method stub
            Log.e("test_TTS", "resumeTTS");
        }

        @Override
        public void releaseTTSPlayer() {
            // TODO Auto-generated method stub
            Log.e("test_TTS", "releaseTTSPlayer");
        }

        @Override
        public int playTTSText(String speech, int bPreempt) {
            // TODO Auto-generated method stub
            Log.e("test_TTS", "playTTSText" + "_" + speech + "_" + bPreempt);

            return 1;
        }

        @Override
        public void phoneHangUp() {
            // TODO Auto-generated method stub
            Log.e("test_TTS", "phoneHangUp");
        }

        @Override
        public void phoneCalling() {
            // TODO Auto-generated method stub
            Log.e("test_TTS", "phoneCalling");
        }

        @Override
        public void pauseTTS() {
            // TODO Auto-generated method stub
            Log.e("test_TTS", "pauseTTS");
        }

        @Override
        public void initTTSPlayer() {
            // TODO Auto-generated method stub
            Log.e("test_TTS", "initTTSPlayer");
        }

        @Override
        public int getTTSState() {
            // TODO Auto-generated method stub
            Log.e("test_TTS", "getTTSState");
            return 1;
        }
    };
}
