package com.smtp.smtp;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.snackbar.Snackbar;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.services.android.navigation.ui.v5.NavigationView;
import com.mapbox.services.android.navigation.ui.v5.NavigationViewOptions;
import com.mapbox.services.android.navigation.ui.v5.OnNavigationReadyCallback;
import com.mapbox.services.android.navigation.ui.v5.listeners.NavigationListener;
import com.mapbox.services.android.navigation.v5.navigation.MapboxNavigationOptions;
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute;
import com.mapbox.services.android.navigation.v5.navigation.camera.Camera;
import com.mapbox.services.android.navigation.v5.navigation.camera.RouteInformation;
import com.mapbox.services.android.navigation.v5.offroute.OffRoute;
import com.mapbox.services.android.navigation.v5.routeprogress.ProgressChangeListener;
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgress;
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgressState;
import com.mapbox.services.android.navigation.v5.utils.RouteUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@SuppressLint("MissingPermission")
public class Navigation extends AppCompatActivity implements NavigationListener, OnNavigationReadyCallback, ProgressChangeListener{

    private NavigationView navigationView;
    private TextView timeDiffTextView;
    private DirectionsRoute route;
    private final boolean SHOULD_SIMULATE = false;
    private final int INITIAL_ZOOM = 18;
    private final double INITIAL_TILT = 30;
    private final int DISTANCE_TOLERANCE = 5000;
    private static final String TAG = "Navigation";
    private boolean isOffRoute = false;
    private String preOffRoute = "";
    private OffRoute neverOffRouteEngine = new OffRoute() {
        @Override
        public boolean isUserOffRoute(Location location, RouteProgress routeProgress, MapboxNavigationOptions options) {
            // User will never be off-route

            return false;
        }
    };

    private Point ORIGIN;
    private Point DESTINATION;

    private String userId;
    private String chantierId;
    private String typeRoute;
    private String token;
    private JSONObject coordinates;
    private double remainingTime;
    private double timeDiffTruckAhead = Double.POSITIVE_INFINITY;
    private String myEtat;
    private Etape etape = null;
    private String etapeIdPrecedente = null;
    private int rayonChargement;
    private int rayonD??chargement;
    private WaypointFilter filter;
    private FusedLocationProviderClient fusedLocationClient;
    private Socket mSocket;
    private boolean connectedToChantier = false;
    private static final String BASE_URL = "http://smtp-dev-env.eba-5jqrxjhz.eu-west-3.elasticbeanstalk.com/";
    private List<Point> roadPoint = new ArrayList<>();
    private Location location;
    private int remainingWaypoints = -1;

    private int timeToSend = 3;
    private int delay = timeToSend;

    // for paused button
    private String previousEtat;
    private Button buttonPause;
    private Button buttonReprendre;
    private boolean onPause = false;
    private NetworkStateReceiver networkStateReceiver = new NetworkStateReceiver();

    // Connection to the socket server
    {
        try {
            Log.i(TAG, "Instanciating a new socket");
            mSocket = IO.socket(BuildConfig.API_URL);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    // Declaring our custom UnhandledExceptionHandler : it send a disconnection event to the socket server
    private CustomUEH UEH = new CustomUEH(new Runnable() {
        @Override
        public void run() {
            disconnectFromChantier();
        }
    });

    public boolean isMyUserId(String userId) {
        return userId.equals(this.userId);
    }

    class User {
        public String userId;
        public Double ETA;
        public String etat;

        public User(String userId, Double ETA, String etat) {
            this.userId = userId;
            this.ETA = ETA;
            this.etat = etat;
        }

        public Double getETA() {
            return ETA;
        }

        public String getUserId() {
            return userId;
        }

        public String getEtat() {
            return etat;
        }


        @Override
        public String toString() {
            return "User{ moi ?" + isMyUserId(this.userId) + ", ETA=" + ETA + ", etat='" + etat + '}';
        }
    }

    class EtaSorter implements Comparator<User> {
        @Override
        public int compare(User o1, User o2) {
            return (int) (o1.getETA() - o2.getETA());
        }
    }

    public class ListUser {
        public ArrayList<User> list;

        public ListUser() {
            this.list = new ArrayList<>();
        }

        public boolean isAddable(User user) {
            return user.getEtat().equals(myEtat);
        }

        public int addList(User user) {
            if (isAddable(user) && !isContainedUser(user.getUserId())) {
                list.add(user);
                Collections.sort(list, new EtaSorter());
                return 1;
            } else {
                return -1;
            }
        }

        public boolean isContainedUser(String userId) {
            boolean res = false;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getUserId().equals(userId)) {
                    res = true;
                }
            }
            return res;
        }

        public void updateMyIndice(String userId) {
            for (int i = 0; i < list.size(); i++) {
                if (isMyUserId(list.get(i).getUserId())) {
                    myIndice = i;
                    Log.d(TAG, "changement indice : " + myIndice);
                }
            }
        }

        public boolean sameEtat(User user) {
            return user.getEtat().equals(myEtat);
        }

        public void updateList(User user) {
            if (!sameEtat(user)) {
                this.deleteUser(user.getUserId());
            } else {
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).getUserId().equals(user.getUserId())) {
                        list.get(i).ETA = user.getETA();
                    }
                }
            }
            Collections.sort(list, new EtaSorter());
        }

        public void deleteUser(String userId) {
            int res = -1;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getUserId().equals(userId)) {
                    res = i;
                }
            }
            list.remove(res);
            Collections.sort(list, new EtaSorter());
        }
    }

    private ListUser myList = new ListUser();
    private int myIndice;

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "OnCreate");
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        Log.d(TAG, "LargeMemoryClass : " + am.getLargeMemoryClass());

        Thread.setDefaultUncaughtExceptionHandler(UEH);

        Intent i = getIntent();
        double[] origin = i.getDoubleArrayExtra("origin");
        double[] destination = i.getDoubleArrayExtra("destination");

        ORIGIN = Point.fromLngLat(origin[0], origin[1]);
        DESTINATION = Point.fromLngLat(destination[0], destination[1]);
        userId = i.getStringExtra("userId");
        chantierId = i.getStringExtra("chantierId");
        typeRoute = i.getStringExtra("typeRoute");
        token = i.getStringExtra("token");
        myEtat = i.getStringExtra("myEtat");
        previousEtat = myEtat;
        myList.addList(new User(userId, Double.POSITIVE_INFINITY, myEtat));

        Mapbox.getInstance(this, getString(R.string.mapbox_access_token));

        setContentView(R.layout.activity_main);
        navigationView = findViewById(R.id.navigationView);
        timeDiffTextView = findViewById(R.id.timeDiffTextView);
        buttonPause = findViewById(R.id.buttonPause);
        buttonReprendre = findViewById(R.id.buttonReprendre);
        navigationView.onCreate(savedInstanceState);

        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        //filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkStateReceiver, filter);
        registerReceiver(broadcastReceiver, new IntentFilter("NO_INTERNET"));

        retrieveLocation();
    }

    private void retrieveLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        LocationRequest locationRequest = LocationRequest.create();
        // Location is requested every 0.5 seconds
        locationRequest.setInterval(500);

        LocationCallback locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if(locationResult == null){
                    return;
                }
                onLocationRetrieved(locationResult.getLastLocation(), this);
            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest,
                locationCallback,
                Looper.getMainLooper());
    }

    private void onLocationRetrieved(Location l, LocationCallback locationCallback) {
        location = l;
        fusedLocationClient.removeLocationUpdates(locationCallback);
        CameraPosition initialPosition = new CameraPosition.Builder()
                .target(new LatLng(location.getLatitude(), location.getLongitude()))
                .zoom(INITIAL_ZOOM)
                .build();
        navigationView.initialize(Navigation.this::onNavigationReady, initialPosition);

        mSocket.on("chantier/user/sentCoordinates", onUserSentCoordinates);
        mSocket.on("chantier/connect/success", onConnectToChantierSuccess);
        mSocket.on("chantier/user/disconnected", onUserDisconnected);
        mSocket.connect();
        connectToChantier();

        // initialize listener*/
        addListenerOnButton();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "OnDestroy");

        disconnectFromChantier();
        mSocket.disconnect();
        mSocket.off();

        //unregister receiver;
        unregisterReceiver(broadcastReceiver);
        unregisterReceiver(networkStateReceiver);

        navigationView.onDestroy();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "OnStop");
        navigationView.onStop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "OnPause");
        navigationView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "OnResume");
        navigationView.onResume();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "OnStart");
        navigationView.onStart();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "OnRestart");
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.d(TAG, "OnSaveInstanceState");
    }

    @Override
    public void onCancelNavigation() {
        Log.d(TAG, "OnCancelNavigation");
        disconnectFromChantier();
        this.finish();
    }

    @Override
    public void onNavigationFinished() {
        Log.d(TAG, "OnNavigationFinished");
    }

    @Override
    public void onNavigationRunning() {

    }

    @Override
    public void onNavigationReady(boolean isRunning) {
        Log.d(TAG, "OnNavigationReady");

        fetchRayon();
        fetchRoute();
        modifyTimeDiffTruckAheadIfNecessary();

        IconFactory iconFactory = IconFactory.getInstance(getApplicationContext());
        Icon icon = iconFactory.fromResource(R.drawable.icon_chargement);
        Icon icon2 = iconFactory.fromResource(R.drawable.icon_dechargement);

        navigationView.retrieveNavigationMapboxMap().retrieveMap().addMarker(new MarkerOptions().title("Chargement")
                .position(new LatLng(ORIGIN.latitude(), ORIGIN.longitude()))
                .icon(icon)
        );

        navigationView.retrieveNavigationMapboxMap().retrieveMap().addMarker(new MarkerOptions().title("D??chargement")
                .position(new LatLng(DESTINATION.latitude(), DESTINATION.longitude()))
                .icon(icon2)
        );

    }


    //brodcast Receiver for handle connection change
    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            navigationView.stopNavigation();
            showAlertDialog();
        }
    };

    // Alert to show when internet is disabled
    private void showAlertDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Erreur R??seaux");
        builder.setMessage("Pas de connexion internet")
                .setCancelable(false);
        builder.setPositiveButton("Quitter", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
    }

    // listener for paused and reprendre buttons
    private void addListenerOnButton() {

        buttonPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                // pause is clicked
                if (!onPause){
                    timeDiffTextView.setVisibility(View.INVISIBLE);
                    buttonPause.setVisibility(View.INVISIBLE);
                    buttonPause.setEnabled(false);
                    buttonReprendre.setVisibility(View.VISIBLE);
                    buttonReprendre.setEnabled(true);
                    if(isOffRoute){
                        previousEtat = preOffRoute;
                    }else{
                        previousEtat = myEtat;
                    }
                    myEtat = "pause";
                    onPause = true;
                }
            }
        });

        buttonReprendre.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(onPause){
                    onPause = false;
                    buttonReprendre.setEnabled(false);
                    buttonReprendre.setVisibility(View.INVISIBLE);
                    buttonPause.setEnabled(true);
                    buttonPause.setVisibility(View.VISIBLE);
                    timeDiffTextView.setVisibility(View.VISIBLE);
                    myEtat = previousEtat;
                }
            }
        });
    }

    private void fetchRayon() {
        final String URL = BASE_URL + "chantiers/" + chantierId;
        JsonObjectRequest getRequest = new JsonObjectRequest(Request.Method.GET, URL, null,
                response -> {
                    try {
                        rayonD??chargement = response.getJSONObject("lieuD??chargement").getInt("rayon");
                        rayonChargement = response.getJSONObject("lieuChargement").getInt("rayon");

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    Log.d(TAG, response.toString());
                },
                new com.android.volley.Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.d(TAG, error.toString() + error.networkResponse);
                    }
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> params = new HashMap<String, String>();
                params.put("Content-Type", "application/json; charset=UTF-8");
                params.put("Authorization", "Bearer " + token);
                return params;
            }
        };
        RequestManager.getInstance(this).getRequestQueue().add(getRequest);
    }

    private float getDistanceFromDestination(Location location) {
        float[] distanceFromDestination = new float[3];
        Point destination = null;
		Log.d("offT", " myEtat "+ myEtat);
        Log.d("offT", " isOfRoute "+ isOffRoute);
        Log.d("offT", " preOffRoute "+ preOffRoute);
        if(isOffRoute){
            if (myEtat.equals("charg??") || preOffRoute.equals("charg??")) {
                destination = DESTINATION;
            }
            if(preOffRoute.equals("enD??chargement") || myEtat.equals("enD??chargement")){
                myEtat = "enD??chargement";
                preOffRoute = "";
                isOffRoute = false;
                destination = DESTINATION;
            }
            if (myEtat.equals("d??charg??") || preOffRoute.equals("d??charg??") ) {
                destination = ORIGIN;
            }
            if(preOffRoute.equals("enChargement") || myEtat.equals("enChargement")){
                myEtat = "enChargement";
                preOffRoute = "";
                isOffRoute = false;
                destination = DESTINATION;
            }
        }else if(myEtat.equals("pause")) {
            if(previousEtat.equals("charg??") || previousEtat.equals("enD??chargement")){
                destination = DESTINATION;
            }else if (previousEtat.equals("d??charg??") || previousEtat.equals("enChargement")) {
                destination = ORIGIN;
            }
        }else{
            if (myEtat.equals("charg??") || myEtat.equals("enD??chargement")) {
                destination = DESTINATION;
            }else if (myEtat.equals("d??charg??") || myEtat.equals("enChargement")) {
                destination = ORIGIN;
            }
        }
        if (destination == null) {
            throw new Error("getDistanceFromDestination: destination cannot be null");
        }
        Location.distanceBetween(
                location.getLatitude(),
                location.getLongitude(),
                destination.latitude(),
                destination.longitude(),
                distanceFromDestination
        );
        return distanceFromDestination[0];
    }

	public void handleOffRoute(RouteProgress routeProgress){
        Log.d("offR", " preOffRoute : " + preOffRoute);
        Log.d("offR", " isOffRoute  : " + isOffRoute);
        Log.d("offR", " routeProgress? : " + (routeProgress.currentState() != null));
        Log.d("offR", " previousEtat : " + previousEtat);

        if(myEtat.equals("enChargement") || myEtat.equals("enD??chargement") || myEtat.equals("pause") ){
            preOffRoute = "";
            isOffRoute = false;
        }else{
            // je suis sur la route et isOffRoute = true => je change isOfRoute en false
            if(routeProgress.currentState() != null && isOffRoute) {
                myEtat = preOffRoute;
                preOffRoute = "";
                isOffRoute = false;
                Log.d("offR", " sortie de route " + isOffRoute);
            }else{
                // je ne suis pas sur la route
                if(routeProgress.currentState() == null && !isOffRoute){
                    preOffRoute = myEtat;
                    myEtat = "offRoute";
                    if(!preOffRoute.equals("")) {
                        isOffRoute = true;
                        Log.d("offR", " sortie de route " + isOffRoute);
                    }
                }
            }
        }
    }

    @Override
    public void onProgressChange(Location location, RouteProgress routeProgress) {
		handleOffRoute(routeProgress);
        boolean didEtatChanged;
        float distanceFromDestination = getDistanceFromDestination(location);
        this.location = location;
        this.remainingTime = routeProgress.durationRemaining() * 1.25;

        didEtatChanged = changeMyEtatIfNecessary(distanceFromDestination);
        if (rerouteUserIfNecessary(didEtatChanged)) {
            return;
        }
        modifyTimeDiffTruckAheadIfNecessary();


        //Register remaining waypoints in SharedPreferences
        if(remainingWaypoints != routeProgress.remainingWaypoints()) {
            remainingWaypoints = routeProgress.remainingWaypoints();
            registerRemainingWaypointInSharedPreferences();
        }

        //Remove waypoints from SharedPreferences if user has arrived
        if(routeProgress.currentState() != null
                && routeProgress.currentState().equals(RouteProgressState.ROUTE_ARRIVED)
                && remainingWaypoints == 1){
            Log.d(TAG, "Arrived!");
            removeRemainingWaypointsFromSharedPreferences();
        }

        //For debug purpose
        RouteUtils routeUtils = new RouteUtils();
        List<Point> remaininPoints = routeUtils.calculateRemainingWaypoints(routeProgress);
        Log.d(TAG, Integer.toString(remainingWaypoints));
        for (Point p: remaininPoints
             ) {
            Log.d(TAG, p.toString());
        }

        coordinates = new JSONObject();
        try {
            coordinates.put("longitude", location.getLongitude());
            coordinates.put("latitude", location.getLatitude());
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
            return;
        }

        if (connectedToChantier) {
            if(timeToSend()){
                sendCoordinates();
            }
            myList.updateList(new User(userId, remainingTime, myEtat));
        }
    }

    private boolean timeToSend() {
        if (delay >= timeToSend) {
            delay = 0;
            return true;
        }else{
            Log.d("delay","delay : "+delay);
            delay++;
            return false;
        }
    }

    public void prepareRoute(JSONArray array) throws JSONException {
        // Ajoute chaque donn??e ?? la liste de waypoint
        List<Waypoint> initialWaypoints = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject waypoint = array.getJSONObject(i);
            initialWaypoints.add(
                    new Waypoint(
                            waypoint.getDouble("longitude"),
                            waypoint.getDouble("latitude"),
                            waypoint.getInt("ordre")
                    )
            );
        }
        Collections.sort(initialWaypoints);

        filter = new WaypointFilter(
                initialWaypoints,
                location,
                DISTANCE_TOLERANCE,
                getRemainingWaypointsFromSharedPreferences(initialWaypoints)
        );

        initialWaypoints = filter.cleanWaypoints();

        for (Waypoint wp:
             initialWaypoints) {
            Log.d(TAG, "Cleaned waypoints: " + wp.toString());
        }

        ArrayList<Point> points = new ArrayList<>();
        points.add(Point.fromLngLat(location.getLongitude(), location.getLatitude()));
        for (Waypoint waypoint : initialWaypoints) {
            points.add(waypoint.getPoint());
        }
        roadPoint = points;
    }

    private void registerRemainingWaypointInSharedPreferences() {
        Log.d(TAG, "Registering remaining points : " + remainingWaypoints + " in " + chantierId + typeRoute + "remainingWaypoints");
        SharedPreferences sharedPref = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt(chantierId + typeRoute + "remainingWaypoints", remainingWaypoints);
        editor.apply();
    }

    public List<Waypoint> getRemainingWaypointsFromSharedPreferences(List<Waypoint> initialWaypointList){
        Log.d(TAG, "Getting remaining points in " + chantierId + typeRoute + "remainingWaypoints");
        SharedPreferences sharedPref = getPreferences(MODE_PRIVATE);
        int nbRemainingWp = sharedPref.getInt(chantierId + typeRoute + "remainingWaypoints", -1);
        Log.d(TAG, "nbRemainingWaypoints:" + nbRemainingWp);
        if (nbRemainingWp == -1) {
            return initialWaypointList;
        } else if (nbRemainingWp > initialWaypointList.size()) {
            removeRemainingWaypointsFromSharedPreferences();
            return initialWaypointList;
        }
        List<Waypoint> res = new ArrayList<>(initialWaypointList.subList(initialWaypointList.size()-nbRemainingWp, initialWaypointList.size()));
        Collections.sort(res);
        return res;
    }

    private void removeRemainingWaypointsFromSharedPreferences() {
        Log.d(TAG, "Removing remaining points of : " + chantierId + typeRoute + "remainingWaypoints");
        SharedPreferences sharedPref = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.remove(chantierId + typeRoute + "remainingWaypoints");
        editor.commit();
    }

    private void fetchRoute() {
        if (myEtat.equals("charg??") || myEtat.equals("enD??chargement")) {
            typeRoute = "aller";
        }
        if (myEtat.equals("d??charg??") || myEtat.equals("enChargement")) {
            typeRoute = "retour";
        }
        initWaypoints();
    }

    private void buildRoute() {


        NavigationRoute.Builder builder = NavigationRoute.builder(this)
                .accessToken("pk." + getString(R.string.gh_key))
                .baseUrl(getString(R.string.base_url))
                //.baseUrl("https://router.project-osrm.org/route/v1/")
                .user("gh")
                .origin(roadPoint.get(0))
                .destination(roadPoint.get(roadPoint.size() - 1))
                .profile("car");
        // add waypoints without first and last point
        if (roadPoint.size() > 2) {
            for (int i = 1; i < roadPoint.size() - 1; i++) {
                builder.addWaypoint(roadPoint.get(i));
            }
        }
        NavigationRoute navRoute = builder.build();

        navRoute.getRoute(
                new Callback<DirectionsResponse>() {
                    @Override
                    public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
                        Log.d(TAG, call.request().url().toString());
                        Log.d(TAG, response.message());
                        if (validRouteResponse(response)) {
                            route = response.body().routes().get(0);
                            launchNavigation();
                        } else {
                            Snackbar.make(navigationView, "Erreur au calcul de la route", Snackbar.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<DirectionsResponse> call, Throwable throwable) {
                        Snackbar.make(navigationView, "Erreur au calcul de la route", Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    private void launchNavigation() {
        NavigationViewOptions.Builder navViewBuilderOptions = NavigationViewOptions.builder()
                .navigationListener(this)
                .progressChangeListener(this)
                .waynameChipEnabled(false)
                .shouldSimulateRoute(SHOULD_SIMULATE)
                .directionsRoute(route);

        Camera camera = new Camera() {
            @Override
            public double tilt(RouteInformation routeInformation) {
                return INITIAL_TILT;
            }

            @Override
            public double zoom(RouteInformation routeInformation) {
                return INITIAL_ZOOM;
            }

            @Override
            public List<Point> overview(RouteInformation routeInformation) {
                return null;
            }
        };

        navigationView.startNavigation(navViewBuilderOptions.build());
        navigationView.retrieveMapboxNavigation().setCameraEngine(camera);
        navigationView.retrieveMapboxNavigation().setOffRouteEngine(neverOffRouteEngine);
    }

    private boolean validRouteResponse(Response<DirectionsResponse> response) {
        return response.body() != null && !response.body().routes().isEmpty();
    }

    public void initWaypoints() {
        final String URL = BASE_URL + "chantiers/" + chantierId + "/route/" + typeRoute;
        RequestQueue requestQueue = Volley.newRequestQueue(this);
        // prepare the Request
        JsonArrayRequest getRequest = new JsonArrayRequest(Request.Method.GET, URL, null,
                response -> {
                    // display response
                    try {
                        prepareRoute(response);
                        buildRoute();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    Log.d(TAG, response.toString());
                },
                new com.android.volley.Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.d(TAG, error.toString() + error.networkResponse);
                    }
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> params = new HashMap<String, String>();
                params.put("Content-Type", "application/json; charset=UTF-8");
                params.put("Authorization", "Bearer " + token);
                return params;
            }
        };

        // add it to the RequestQueue
        requestQueue.add(getRequest);
    }

    private void changeEtape(){
        if(etapeIdPrecedente == null){
            etape = new Etape(chantierId,userId,myEtat, null, getApplicationContext());
        }else{
            // send existing etape
            etape.sendFinEtape();
            etape = new Etape(chantierId, userId, myEtat, etapeIdPrecedente, getApplicationContext());
        }
        etapeIdPrecedente = etape.getEtapeId();
    }

    private boolean changeMyEtatIfNecessary(double distanceRemaining) {
        boolean etatChanged = false;
        String previousEtat = myEtat;
        int rayonChangementEtat = 0;
        if (typeRoute.equals("aller")) {
            rayonChangementEtat = rayonD??chargement;
        } else if (typeRoute.equals("retour")) {
            rayonChangementEtat = rayonChargement;
        }
        Log.d("offD", "distance : "+distanceRemaining+", rayonChangementEtat : "+ rayonChangementEtat+ ", typeRoute : " + typeRoute + ", myEtat : "+ myEtat);
        if (distanceRemaining < rayonChangementEtat) {
            if (myEtat.equals("charg??") || preOffRoute.equals("charg??")) {
                myEtat = "enD??chargement";
                changeEtape();
                etatChanged = true;
                return true;
            } else if (myEtat.equals("d??charg??") || preOffRoute.equals("d??charg??")) {
                myEtat = "enChargement";
                changeEtape();
                etatChanged = true;
                return true;
            }
        } else {
            if (myEtat.equals("enChargement")) {
                myEtat = "charg??";
                changeEtape();
                etatChanged = true;
                return true;
            } else if (myEtat.equals("enD??chargement")) {
                myEtat = "d??charg??";
                changeEtape();
                etatChanged = true;
                return true;
            }
        }
        if (etatChanged) {
            Log.d(TAG, "Etat changed: from " + previousEtat + " to " + myEtat);
        }
        return false;
    }

    private boolean rerouteUserIfNecessary(boolean etatChanged) {
        boolean userRerouted = false;
        if (etatChanged) {
            if (myEtat.equals("charg??") || myEtat.equals("d??charg??")) {
                roadPoint.clear();
                navigationView.stopNavigation();
                navigationView.retrieveNavigationMapboxMap().clearMarkers();
                Log.d(TAG, "User rerouted");
                userRerouted = true;
            }
        }
        if (userRerouted) {
            fetchRoute();
        }
        return userRerouted;
    }

    private void modifyTimeDiffTruckAheadIfNecessary() {
        if (myEtat.equals("enChargement")) {
            timeDiffTextView.setText("Vous ??tes en cours de chargement \nQuittez la zone une fois charg??");
        } else if (myEtat.equals("enD??chargement")) {
            timeDiffTextView.setText("Vous ??tes en cours de d??chargement \nQuittez la zone une fois d??charg??");
        } else if (myIndice > 0 && myList.list.size() > 1) {
            User userAhead = myList.list.get(myIndice - 1);
            timeDiffTruckAhead = Math.abs(remainingTime - userAhead.getETA());
            int minutes = (int) Math.floor(timeDiffTruckAhead / 60);
            int secondes = (int) Math.floor(timeDiffTruckAhead % 60);
            if (minutes < 1) {
                timeDiffTextView.setText("Vous ??tes " + myEtat + "\nVous avez " + secondes + " secondes d'??cart avec le camion de devant");
            } else {
                timeDiffTextView.setText("Vous ??tes " + myEtat + "\nVous avez " + minutes + " mn " + secondes + " d'??cart avec le camion de devant");
            }
        } else {
            timeDiffTextView.setText("Vous ??tes " + myEtat + " \nIl n'y a pas de camion devant vous");
        }
    }

    private void sendCoordinates() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("coordinates", coordinates);
            obj.put("etat", myEtat);
            obj.put("ETA", remainingTime);

        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
            return;
        }
        mSocket.emit("chantier/sendCoordinates", obj);
    }

    private void disconnectFromChantier() {
        Log.d(TAG, "Disconnecting from chantier");
        connectedToChantier = false;
        mSocket.emit("chantier/disconnect");
    }

    private void connectToChantier() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("userId", userId);
            obj.put("chantierId", chantierId);
            obj.put("coordinates", coordinates);
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
            return;
        }
        Log.d(TAG, "Connecting to chantier");
        mSocket.emit("chantier/connect", obj);
    }

    private Emitter.Listener onUserSentCoordinates = args -> runOnUiThread(() -> {
        JSONObject data = (JSONObject) args[0];
        double senderETA;
        String senderEtat;
        String senderId;
        try {
            senderETA = data.getDouble("ETA");
            senderEtat = data.getString("etat");
            senderId = data.getString("userId");
            User sender = new User(senderId, senderETA, senderEtat);
            if (myList.isContainedUser(senderId)) {
                myList.updateList(sender);
            } else {
                myList.addList(sender);
            }
            myList.updateMyIndice(Navigation.this.userId);
            modifyTimeDiffTruckAheadIfNecessary();
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
            return;
        }
    });

    private Emitter.Listener onConnectToChantierSuccess = args -> runOnUiThread(() -> {
        Log.d(TAG, "Connection to chantier successful");
        connectedToChantier = true;
        Log.d(TAG, "Etat after connection : " + myEtat);
    });

    private Emitter.Listener onUserDisconnected = args -> runOnUiThread(() -> {
        JSONObject data = (JSONObject) args[0];
        String senderId;
        try {
            senderId = data.getString("userId");
            if (myList.isContainedUser(senderId)) {
                myList.deleteUser(senderId);
            } else {
                Log.d(TAG, " impossible to delete : user not in the list ");
            }

        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
            return;
        }
        modifyTimeDiffTruckAheadIfNecessary();
    });

}
