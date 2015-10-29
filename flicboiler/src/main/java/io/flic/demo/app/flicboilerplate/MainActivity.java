package io.flic.demo.app.flicboilerplate;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.os.StrictMode;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.ActionBarActivity;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
//import com.google.android.gms.identity.intents.Address;
import com.google.android.gms.identity.intents.Address.AddressOptions;
import com.google.android.gms.identity.intents.UserAddressRequest;
import com.google.android.gms.identity.intents.model.UserAddress;
import com.google.android.gms.plus.Plus;
import com.google.android.gms.wallet.WalletConstants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import io.flic.demo.app.flicboilerplate.locaddress.Constants;
import io.flic.demo.app.flicboilerplate.locaddress.FetchAddressIntentService;
import io.flic.lib.FlicButton;
import io.flic.lib.FlicButtonCallback;
import io.flic.lib.FlicButtonCallbackFlags;
import io.flic.lib.FlicManager;
import io.flic.lib.FlicManagerInitializedCallback;


public class MainActivity extends ActionBarActivity implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, TextToSpeech.OnInitListener {

    protected static final String ADDRESS_REQUESTED_KEY = "address-request-pending";
    protected static final String LOCATION_ADDRESS_KEY = "location-address";
    public static final int REQUEST_CODE_RESOLVE_ADDRESS_LOOKUP = 1006;
    public static final int REQUEST_CODE_RESOLVE_ERR = 1007;
    private static final String USER_PREFS = "com.google.android.gms.samples.wallet.USER_PREFS";
    private static final String KEY_USERNAME = "com.google.android.gms.samples.wallet.KEY_USERNAME";
    private String mUserName;

    private SharedPreferences mPrefs;

    /**
     * Provides the entry point to Google Play services.
     */
    protected GoogleApiClient mGoogleApiClient;

    /**
     * Represents a geographical location.
     */
    protected Location mLastLocation;

    /**
     * Tracks whether the user has requested an address. Becomes true when the user requests an
     * address and false when the address (or an error message) is delivered.
     * The user requests an address by pressing the Fetch Address button. This may happen
     * before GoogleApiClient connects. This activity uses this boolean to keep track of the
     * user's intent. If the value is true, the activity tries to fetch the address as soon as
     * GoogleApiClient connects.
     */
    protected boolean mAddressRequested;


    public String getmAddressOutput() {
        return mAddressOutput;
    }

    /**
     * The formatted location address.
     */
    protected String mAddressOutput;

    /**
     * Receiver registered with this activity to get the response from FetchAddressIntentService.
     */
    private AddressResultReceiver mResultReceiver;

    /**
     * Displays the location address.
     */
    protected TextView mLocationAddressTextView;

    /**
     * Visible while the address is being fetched.
     */
    ProgressBar mProgressBar;

    /**
     * Visible when fetching work/home address from google Address.API
     */
    private ProgressDialog mProgressDialog;

    /**
     * Kicks off the request to fetch an address when pressed.
     */
    Button mFetchAddressButton;

    private static final String TAG = "mainActivity";
    private FlicManager manager;

    private String mAddressLatLabel;
    private String mAddressLonLabel;
    private String homeAddress;
    private String homeCity;
    private String homeState;
    private String homePostalCode;

    private TextView mHomeAddrTV;
    private TextView mDistancettsTV;
    private TextView mGpsTV;
    private TextView mETATV;
    private TextView sendSMS;

    private TextToSpeech tts;
    private String ttsText;

    //fully qualified import for google address API use
    private com.google.android.gms.identity.intents.Address address;


    private FlicButtonCallback buttonCallback = new FlicButtonCallback() {

        @Override
        public void onButtonUpOrDown(FlicButton button, boolean wasQueued, int timeDiff, boolean isUp, boolean isDown) {
            final String text = button + " was " + (isDown ? "pressed" : "released");
            Log.d(TAG, text);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    TextView tv = (TextView) findViewById(R.id.textView);
                    tv.setText(text);
                    toasty();
                }
            });
        }
    };

    public void grabButton(View v) {
        if (manager != null) {
            manager.initiateGrabButton(this);
        }
    }
    private void toasty() {
        showToast("Flic Pressed!");
    }

    private void setButtonCallback(FlicButton button) {
        button.removeAllFlicButtonCallbacks();
        button.addFlicButtonCallback(buttonCallback);
        button.setFlicButtonCallbackFlags(FlicButtonCallbackFlags.UP_OR_DOWN);
        button.setActiveMode(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPrefs = getSharedPreferences(USER_PREFS, MODE_PRIVATE);
        mUserName = mPrefs.getString(KEY_USERNAME, null);

        setContentView(R.layout.activity_main);
        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }
        FlicManager.setAppCredentials(getString(R.string.flic_key), getString(R.string.flic_secret));
        FlicManager.getInstance(this, new FlicManagerInitializedCallback() {

            @Override
            public void onInitialized(FlicManager manager) {
                Log.d(TAG, "Ready to use manager");

                MainActivity.this.manager = manager;

                // Restore buttons grabbed in a previous run of the activity
                List<FlicButton> buttons = manager.getKnownButtons();
                for (FlicButton button : buttons) {
                    String status = null;
                    switch (button.getConnectionStatus()) {
                        case FlicButton.BUTTON_DISCONNECTED:
                            status = "disconnected";
                            break;
                        case FlicButton.BUTTON_CONNECTION_STARTED:
                            status = "connection started";
                            break;
                        case FlicButton.BUTTON_CONNECTION_COMPLETED:
                            status = "connection completed";
                            break;
                    }
                    Log.d(TAG, "Found an existing button: " + button + ", status: " + status);
                    setButtonCallback(button);
                }
            }
        });

        mAddressLatLabel = getResources().getString(R.string.latitude_label);
        mAddressLonLabel = getResources().getString(R.string.longitude_label);

        mResultReceiver = new AddressResultReceiver(new Handler());

        mGpsTV = (TextView) findViewById(R.id.addrView);
        mETATV = (TextView) findViewById(R.id.est_view);
        sendSMS = (TextView) findViewById(R.id.send_sms_tv);

        mDistancettsTV = (TextView) findViewById(R.id.distance_tv);
        mDistancettsTV.setText("TTS test");

        mHomeAddrTV = (TextView) findViewById(R.id.home_address_view);
        mLocationAddressTextView = (TextView) findViewById(R.id.location_address_view);
        mProgressBar = (ProgressBar) findViewById(R.id.progress_bar);
        mProgressDialog = initializeProgressDialog();

        // Set defaults, then update using values stored in the Bundle.
        mAddressRequested = false;
        mAddressOutput = "";
        updateValuesFromBundle(savedInstanceState);

        updateUIWidgets();
        buildGoogleApiClient();

        tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.ERROR) {
                    tts.setLanguage(Locale.US);
                } else if (status == TextToSpeech.SUCCESS) {
                    showToast("TTS Success");
                }
            }
        });

        sendSMS.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                 //mike
                sendETASMS("2486888834", mETATV.getText().toString() );
                    //rich
                //sendETASMS("5868380674", mETATV.getText().toString() );
            }
        });
    }

    @Override
    protected void onDestroy() {
        FlicManager.destroyInstance();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }

        super.onDestroy();
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        FlicButton button = manager.completeGrabButton(requestCode, resultCode, data);
        if (button != null) {
            Log.d(TAG, "Got a button: " + button);
            setButtonCallback(button);
        }

        switch (requestCode) {
            case REQUEST_CODE_RESOLVE_ERR:
                // call connect regardless of success or failure
                // if the result was success, the connect should succeed
                // if the result was not success, this should get a new connection result
                mGoogleApiClient.connect();
                break;
            case REQUEST_CODE_RESOLVE_ADDRESS_LOOKUP:
                dismissProgressDialog();

                switch (resultCode) {
                    case Activity.RESULT_OK:

                        UserAddress userAddress = UserAddress.fromIntent(data);
                        Toast.makeText(this, getString(R.string.user_add,
                                formatUsAddress(userAddress)), Toast.LENGTH_LONG).show();
                        break;
                    case Activity.RESULT_CANCELED:
                        break;
                    default:
                        Toast.makeText(this, getString(R.string.no_address),
                                Toast.LENGTH_LONG).show();
                        break;
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                dismissProgressDialog();
                break;
        }

    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();

        tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.ERROR) {
                    tts.setLanguage(Locale.US);
                } else if (status == TextToSpeech.SUCCESS) {
                    showToast("TTS Success");
                }
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    /**
     * Runs when a GoogleApiClient object successfully connects.
     */
    @Override
    public void onConnected(Bundle connectionHint) {
        // Gets the best and most recent location currently available, which may be null
        // in rare cases when a location is not available.
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (mLastLocation != null) {
            // Determine whether a Geocoder is available.
            if (!Geocoder.isPresent()) {
                Toast.makeText(this, R.string.no_geocoder_available, Toast.LENGTH_LONG).show();
                return;
            }

            mGpsTV.setText(String.format("%s: %f \n%s: %f",
                    mAddressLatLabel, mLastLocation.getLatitude(),
                    mAddressLonLabel, mLastLocation.getLongitude()));
            locationByLatLong(42.5037539, -83.1099837);
            mETATV.setText("Arrival Time: "
                    + getDistanceInfo(mLastLocation.getLatitude(), mLastLocation.getLongitude(),
                    42.5037539, -83.1099837));

            // It is possible that the user presses the button to get the address before the
            // GoogleApiClient object successfully connects. In such a case, mAddressRequested
            // is set to true, but no attempt is made to fetch the address (see
            // fetchAddressButtonHandler()) . Instead, we start the intent service here if the
            // user has requested an address, since we now have a connection to GoogleApiClient.
            if (mAddressRequested) {
                startIntentService();
            }
        }
        fetchAddressButtonHandler(findViewById(android.R.id.content));

    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Refer to the javadoc for ConnectionResult to see what error codes might be returned in
        // onConnectionFailed.
        Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = " + result.getErrorCode());
    }


    @Override
    public void onConnectionSuspended(int cause) {
        // The connection to Google Play services was lost for some reason. We call connect() to
        // attempt to re-establish the connection.
        Log.i(TAG, "Connection suspended");
        mGoogleApiClient.connect();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save whether the address has been requested.
        savedInstanceState.putBoolean(ADDRESS_REQUESTED_KEY, mAddressRequested);

        // Save the address string.
        savedInstanceState.putString(LOCATION_ADDRESS_KEY, mAddressOutput);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            Log.e("TTS", "Initilization Success!");
            int result = tts.setLanguage(Locale.US);

            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "This Language is not supported");
            } else {
                String anyThing = " ";
                speakIt(anyThing);
            }
            return;
        } else {
            Log.e("TTS", "Initilization Failed!");
        }
    }


    public void onPause() {
        if (tts != null) {
            tts.stop();
            // tts.shutdown();
        }
        super.onPause();
    }

    public void sendETASMS( String phoneNumber, String message){
        SmsManager sms = SmsManager.getDefault();
        sms.sendTextMessage(phoneNumber, null, message, null, null);
    }

    public void speakETA(View v) {
        String toSpeak = ttsText + " to destination";
        Toast.makeText(getApplicationContext(), toSpeak, Toast.LENGTH_SHORT).show();
        tts.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
    }

    private ProgressDialog initializeProgressDialog() {
        ProgressDialog dialog = new ProgressDialog(this);
        dialog.setIndeterminate(true);
        dialog.setMessage(getString(R.string.loading));
        return dialog;
    }

    private void showProgressDialog() {
        if (mProgressDialog != null && !mProgressDialog.isShowing()) {
            mProgressDialog.show();
        }
    }

    private void dismissProgressDialog() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
    }

    public void lookupAddress(View v) {
        if (mGoogleApiClient.isConnected()) {
            showProgressDialog();
            UserAddressRequest request = UserAddressRequest.newBuilder().build();

            address.requestUserAddress(mGoogleApiClient, request,
                    REQUEST_CODE_RESOLVE_ADDRESS_LOOKUP);
        } else {
            if (!mGoogleApiClient.isConnecting()) {
                mGoogleApiClient.connect();
            }

        }
    }

    // Address formatting specific to the US, depending upon the countries supported you may
    // have different address formatting
    private static String formatUsAddress(UserAddress address) {
        StringBuilder builder = new StringBuilder();
        builder.append("\n");
        if (appendIfValid(address.getAddress1(), builder)) builder.append(", ");
        if (appendIfValid(address.getLocality(), builder)) builder.append(", ");
        if (appendIfValid(address.getAdministrativeArea(), builder)) builder.append(", ");
        appendIfValid(address.getCountryCode(), builder);
        return builder.toString();
    }

    private static boolean appendIfValid(String string, StringBuilder builder) {
        if (string != null && string.length() > 0) {
            builder.append(string);
            return true;
        }
        return false;
    }


    /**
     * Updates fields based on data stored in the bundle.
     */
    private void updateValuesFromBundle(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            // Check savedInstanceState to see if the address was previously requested.
            if (savedInstanceState.keySet().contains(ADDRESS_REQUESTED_KEY)) {
                mAddressRequested = savedInstanceState.getBoolean(ADDRESS_REQUESTED_KEY);
            }
            // Check savedInstanceState to see if the location address string was previously found
            // and stored in the Bundle. If it was found, display the address string in the UI.
            if (savedInstanceState.keySet().contains(LOCATION_ADDRESS_KEY)) {
                mAddressOutput = savedInstanceState.getString(LOCATION_ADDRESS_KEY);
                displayAddressOutput();
            }
        }
    }

    /**
     * Builds a GoogleApiClient. Uses {@code #addApi} to request the LocationServices API.
     */
    protected synchronized void buildGoogleApiClient() {
        //use fully qualified class name to fix import error with android.location.Address
        address = new com.google.android.gms.identity.intents.Address();
        String accntName = "msimons.work@gmail.com";
        AddressOptions options = new AddressOptions(WalletConstants.THEME_LIGHT);
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApiIfAvailable(LocationServices.API)
                .addApiIfAvailable(address.API, options)
                .addScope(Plus.SCOPE_PLUS_LOGIN)
                .setAccountName(accntName)
                .build();
    }

    /**
     * Runs when user clicks the Fetch Address button. Starts the service to fetch the address if
     * GoogleApiClient is connected.
     */
    public void fetchAddressButtonHandler(View view) {
        // We only start the service to fetch the address if GoogleApiClient is connected.
        if (mGoogleApiClient.isConnected() && mLastLocation != null) {
            startIntentService();
        }
        // If GoogleApiClient isn't connected, we process the user's request by setting
        // mAddressRequested to true. Later, when GoogleApiClient connects, we launch the service to
        // fetch the address. As far as the user is concerned, pressing the Fetch Address button
        // immediately kicks off the process of getting the address.
        mAddressRequested = true;
        updateUIWidgets();
    }



    private void locationByLatLong(double latitude, double longitude) {

        Geocoder geocoder;
        ArrayList<Address> addresses = new ArrayList<Address>();
        geocoder = new Geocoder(this, Locale.getDefault());

        try {
            addresses = (ArrayList<Address>) geocoder.getFromLocation(latitude, longitude, 1); // Here 1 represent max location result to returned, by documents it recommended 1 to 5
        } catch (IOException e) {
            e.printStackTrace();
        }

        homeAddress = addresses.get(0).getAddressLine(0); // If any additional address line present than only, check with max available address lines by getMaxAddressLineIndex()
        homeCity = addresses.get(0).getLocality();
        homeState = addresses.get(0).getAdminArea();
        homePostalCode = addresses.get(0).getPostalCode();

        mHomeAddrTV.setText("Home:\n" + String.format("%s, %s \n%s, %s \n",
                homeAddress,
                homeCity,
                homeState,
                homePostalCode));
    }

    private String getDistanceInfo(double lat1, double lng1, double lat2, double lng2) {
        String DIRECTIONS_API_BASE = "https://maps.googleapis.com/maps/api/directions";
        String OUT_JSON = "/json";
        String UNITS = "imperial";
        String distanceInfo = null;

        // API KEY of the project Google Map Api For work
        String API_KEY = getString(R.string.google_server_key);


        HttpURLConnection mUrlConnection = null;
        StringBuilder mJsonResults = new StringBuilder();
        try {
            StringBuilder sb = new StringBuilder(DIRECTIONS_API_BASE + OUT_JSON);
            sb.append("?origin=" + URLEncoder.encode(lat1 + "," + lng1, "utf8"));
            sb.append("&destination=" + URLEncoder.encode(lat2 + "," + lng2, "utf8"));
            sb.append("&units=" + UNITS);
            sb.append("&key=" + API_KEY);

            URL url = new URL(sb.toString());
            mUrlConnection = (HttpURLConnection) url.openConnection();
            InputStreamReader in = new InputStreamReader(mUrlConnection.getInputStream());

            // Load the results into a StringBuilder
            int read;
            char[] buff = new char[1024];
            while ((read = in.read(buff)) != -1) {
                mJsonResults.append(buff, 0, read);
            }

        } catch (MalformedURLException e) {
            Log.e(TAG, "Error processing Distance Matrix API URL");
            return null;

        } catch (IOException e) {
            System.out.println("Error connecting to Distance Matrix");
            return null;
        } finally {
            if (mUrlConnection != null) {
                mUrlConnection.disconnect();
            }
        }
        JSONObject jsonObject = new JSONObject();

        try {

            jsonObject = new JSONObject(mJsonResults.toString());

            JSONArray array = jsonObject.getJSONArray("routes");

            JSONObject routes = array.getJSONObject(0);

            JSONArray legs = routes.getJSONArray("legs");

            JSONObject steps = legs.getJSONObject(0);

            JSONObject distance = steps.getJSONObject("distance");
            JSONObject duration = steps.getJSONObject("duration");

            //capture the Destination ETa and Distance to expose to TTS and the view
            ttsText = duration.getString("text");
            distanceInfo = duration.getString("text") + " (" + distance.getString("text") + ")";


        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        if (distanceInfo != null) {
            return distanceInfo;
        } else
            return "Destination not found";
    }

    /**
     * Creates an intent, adds location data to it as an extra, and starts the intent service for
     * fetching an address.
     */
    public void startIntentService() {
        // Create an intent for passing to the intent service responsible for fetching the address.
        Intent intent = new Intent(this, FetchAddressIntentService.class);

        // Pass the result receiver as an extra to the service.
        intent.putExtra(Constants.RECEIVER, mResultReceiver);

        // Pass the location data as an extra to the service.
        intent.putExtra(Constants.LOCATION_DATA_EXTRA, mLastLocation);

        // Start the service. If the service isn't already running, it is instantiated and started
        // (creating a process for it if needed); if it is running then it remains running. The
        // service kills itself automatically once all intents are processed.
        startService(intent);
    }


    /**
     * Updates the address in the UI.
     */
    protected void displayAddressOutput() {
        mLocationAddressTextView.setText(mAddressOutput);
    }

    /**
     * Toggles the visibility of the progress bar. Enables or disables the Fetch Address button.
     */
    private void updateUIWidgets() {
        if (mAddressRequested) {
            mProgressBar.setVisibility(ProgressBar.VISIBLE);
            // mFetchAddressButton.setEnabled(false);
        } else {
            mProgressBar.setVisibility(ProgressBar.GONE);
            // mFetchAddressButton.setEnabled(true);
        }
    }

    /**
     * Shows a toast with the given text.
     */
    protected void showToast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }



    public boolean isLoggedIn() {
        return mUserName != null;
    }

    public void login(String userName) {
        mUserName = userName;
        mPrefs.edit().putString(KEY_USERNAME, mUserName).commit();
    }

    public void logout() {
        mUserName = null;
        mPrefs.edit().remove(KEY_USERNAME).commit();
    }

    public String getAccountName() {
        return mPrefs.getString(KEY_USERNAME, null);
    }

    private void speakIt(String someThing) {
        Log.e("something: ", someThing);
        tts.speak(someThing, TextToSpeech.QUEUE_ADD, null);
        Log.e("TTS", "called");
    }


    /**
     * Receiver for data sent from FetchAddressIntentService.
     */
    public class AddressResultReceiver extends ResultReceiver {
        public AddressResultReceiver(Handler handler) {
            super(handler);
        }

        /**
         * Receives data sent from FetchAddressIntentService and updates the UI in LocationAddress.
         */
        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {

            // Display the address string or an error message sent from the intent service.
            mAddressOutput = resultData.getString(Constants.RESULT_DATA_KEY);
            displayAddressOutput();

            // Show a toast message if an address was found.
            if (resultCode == Constants.SUCCESS_RESULT) {
                showToast(getString(R.string.address_found));
            }

            // Reset. Enable the Fetch Address button and stop showing the progress bar.
            mAddressRequested = false;
            updateUIWidgets();
        }
    }

    /*
        private String getDirectionsUrl(LatLng origin, LatLng dest) {

            // Origin of route
            String str_origin = "origin=" + origin.latitude + "," + origin.longitude;

            // Destination of route
            String str_dest = "destination=" + dest.latitude + "," + dest.longitude;

            // Sensor enabled
            String sensor = "sensor=false";

            // Building the parameters to the web service
            String parameters = str_origin + "&" + str_dest + "&" + sensor;

            // Output format
            String output = "json";

            // Building the url to the web service
            String url = "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters;

            return url;
        }

        /**
         * A method to download json data from url
         */
    private String downloadUrl(String strUrl) throws IOException {
        String data = "";
        InputStream iStream = null;
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL(strUrl);

            // Creating an http connection to communicate with url
            urlConnection = (HttpURLConnection) url.openConnection();

            // Connecting to url
            urlConnection.connect();

            // Reading data from url
            iStream = urlConnection.getInputStream();

            BufferedReader br = new BufferedReader(new InputStreamReader(iStream));

            StringBuffer sb = new StringBuffer();

            String line = "";
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

            data = sb.toString();

            br.close();

        } catch (Exception e) {
            Log.d("Exception getting url", e.toString());
        } finally {
            iStream.close();
            urlConnection.disconnect();
        }
        return data;
    }

    // Fetches data from url passed
    private class DownloadTask extends AsyncTask<String, Void, String> {

        // Downloading data in non-ui thread
        @Override
        protected String doInBackground(String... url) {

            // For storing data from web service
            String data = "";

            try {
                // Fetching the data from web service
                data = downloadUrl(url[0]);
            } catch (Exception e) {
                Log.d("Background Task", e.toString());
            }
            return data;
        }

        // Executes in UI thread, after the execution of
        // doInBackground()
        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            ParserTask parserTask = new ParserTask();

            // Invokes the thread for parsing the JSON data
            parserTask.execute(result);
        }
    }

    /**
     * A class to parse the Google Places in JSON format
     */
    private class ParserTask extends AsyncTask<String, Integer, List<List<HashMap<String, String>>>> {

        // Parsing the data in non-ui thread
        @Override
        protected List<List<HashMap<String, String>>> doInBackground(String... jsonData) {

            JSONObject jObject;
            List<List<HashMap<String, String>>> routes = null;
/*
            try{
                jObject = new JSONObject(jsonData[0]);
                DirectionsJSONParser parser = new DirectionsJSONParser();

                // Starts parsing data
                routes = parser.parse(jObject);
            }catch(Exception e){
                e.printStackTrace();
            }
            */
            return routes;

        }

        // Executes in UI thread, after the parsing process
        @Override
        protected void onPostExecute(List<List<HashMap<String, String>>> result) {
            ArrayList<LatLng> points = null;
            PolylineOptions lineOptions = null;
            MarkerOptions markerOptions = new MarkerOptions();
            String distance = "";
            String duration = "";

            if (result.size() < 1) {
                Toast.makeText(getBaseContext(), "No Points", Toast.LENGTH_SHORT).show();
                return;
            }

            // Traversing through all the routes
            for (int i = 0; i < result.size(); i++) {
                points = new ArrayList<LatLng>();
                lineOptions = new PolylineOptions();

                // Fetching i-th route
                List<HashMap<String, String>> path = result.get(i);

                // Fetching all the points in i-th route
                for (int j = 0; j < path.size(); j++) {
                    HashMap<String, String> point = path.get(j);

                    if (j == 0) {    // Get distance from the list
                        distance = (String) point.get("distance");
                        continue;
                    } else if (j == 1) { // Get duration from the list
                        duration = (String) point.get("duration");
                        continue;
                    }

                    double lat = Double.parseDouble(point.get("lat"));
                    double lng = Double.parseDouble(point.get("lng"));
                    LatLng position = new LatLng(lat, lng);

                    points.add(position);
                }

                // Adding all the points in the route to LineOptions
                lineOptions.addAll(points);
                lineOptions.width(2);
                lineOptions.color(Color.RED);
            }

            // mDistancettsTV.setText("Distance:" + distance + ", Duration:" + duration);

            // Drawing polyline in the Google Map for the i-th route
            // map.addPolyline(lineOptions);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
}






