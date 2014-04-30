package com.example.findprimes;

import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.content.IntentSender.SendIntentException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.PointLabelFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;
import com.androidplot.xy.XYStepMode;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi.ContentsResult;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.OpenFileActivityBuilder;

public class MainActivity extends Activity implements GoogleApiClient.ConnectionCallbacks,
GoogleApiClient.OnConnectionFailedListener{

	private Button findButton;
	private EditText numInput;
	private TextView primesOutput;
	private Find_N_Primes task;
	private ProgressBar progressBar;
	private Button googleDriveButton;
	private Button graphPrimes;
	private ArrayList <Integer> listOfPrimes = new ArrayList<Integer>();
	private GoogleApiClient mGoogleApiClient;
	private static final String TAG = "android-drive-quickstart";
	private static final int REQUEST_CODE_CREATOR = 2;
	private static final int REQUEST_CODE_RESOLUTION = 3;
	private XYPlot plot;
	private int menuestate = 0; // Used to switch activities

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		findButton = (Button) findViewById(R.id.find_button);
		numInput = (EditText) findViewById(R.id.num_primes_to_find);
		primesOutput = (TextView) findViewById(R.id.output);
		progressBar = (ProgressBar) findViewById(R.id.progressBar);
		googleDriveButton = (Button) findViewById(R.id.drive_upload);
		graphPrimes = (Button) findViewById(R.id.graph_button);

		mGoogleApiClient = new GoogleApiClient.Builder(this)
		.addApi(Drive.API)
		.addScope(Drive.SCOPE_FILE)
		.addConnectionCallbacks(this)
		.addOnConnectionFailedListener(this)
		.build();

		findButton.setOnClickListener(new View.OnClickListener() {
			// This calls the Find_N_Primes class (AsyncTask) to find prime numbers
			@Override
			public void onClick(View v) {
				task = new Find_N_Primes();
				String numToFind = numInput.getText().toString();
				// Error check for null entry
				if(numToFind.toString().equals("")){
					Toast.makeText(MainActivity.this, "Please Enter Value",
							Toast.LENGTH_SHORT).show();
				}else{
					// Starts the async task
					progressBar.setIndeterminate(true);
					listOfPrimes.clear();
					// Makes sure that the user can not click the button while the async task is running
					googleDriveButton.setClickable(false);
					graphPrimes.setClickable(false);
					task.execute(numToFind);
				}
			}
		});
	}

	// Button to upload to Google Drive
	public void uploadToDrive(View view){
		if (listOfPrimes.toString().equals("[]")){
			Toast.makeText(MainActivity.this, "Please Run Prime Generator Before Uploading",
					Toast.LENGTH_SHORT).show();
		}else{
			saveFileToDrive();
		}
	}

	// Stops the AsyncTask before it finishes, and clears the TextView/resets the progress bar
	public void cancel(View view){
		Log.i(TAG, primesOutput.toString());
		if(task.getStatus().equals(AsyncTask.Status.RUNNING)){
			progressBar.setIndeterminate(false);
			task.cancel(true);
			primesOutput.setText("Canceled Before Completion");	
			listOfPrimes.clear();
		}
		else if(task.getStatus().equals(AsyncTask.Status.FINISHED)) {
			primesOutput.setText("");
			listOfPrimes.clear();
		}		
	}

	// Button to demonstrate/test responsiveness of the app
	public void responsive(View view){
		Toast.makeText(MainActivity.this, "Yes!",
				Toast.LENGTH_SHORT).show();
	}

	// Produces a graph of the prime numbers
	public void showGraph(View view){
		if (listOfPrimes.toString().equals("[]")){
			Toast.makeText(MainActivity.this, "Please Run Prime Generator Before Visualizing",
					Toast.LENGTH_SHORT).show();
		}else{
			menuestate = 1; // Entering the second activity
			setContentView(R.layout.simple_xy_plot);

			// initialize our XYPlot reference:
			plot = (XYPlot) findViewById(R.id.mySimpleXYPlot);

			XYSeries series1 = new SimpleXYSeries(
					listOfPrimes,          // SimpleXYSeries takes a list
					SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, // Y_VALS_ONLY means use the element index as the x value
					"Visualizing Primes");                             // Set the display title of the series

			// Create a formatter to use for drawing a series using LineAndPointRenderer
			// and configure it from xml:
			LineAndPointFormatter series1Format = new LineAndPointFormatter();
			series1Format.setPointLabelFormatter(new PointLabelFormatter());
			series1Format.configure(getApplicationContext(),
					R.xml.line_point_formatter);

			// Adds a new series to the xyplot:
			plot.addSeries(series1, series1Format);

			// Hides the plot's legend
			plot.getLegendWidget().setVisible(false);

			// Sets the domain's interval
			plot.setDomainStep(XYStepMode.INCREMENT_BY_VAL, listOfPrimes.size());     
			plot.setDomainValueFormat(new DecimalFormat("0"));
			plot.setDomainStepValue(1);

			// Reduces the number of range labels
			plot.setTicksPerRangeLabel(3);
			plot.getGraphWidget().setDomainLabelOrientation(-45);
		}
	}

	// Handles switching between activities
	public void onBackPressed(){
		if(menuestate == 1){ // Returns to the Main Activity from the graph
			finish();
			Intent intent = new Intent(MainActivity.this, MainActivity.class);
			startActivity(intent);
		}else{
			finish(); // Exits the app if the back button is pressed from within the Main Activity
		}
	}

	// AsyncTask to find prime numbers using the Sieve of Eratosthenes
	private class Find_N_Primes extends AsyncTask<String, Void, String>{
		// Background Process
		protected String doInBackground(String... num){
			// Parses the input string into an int, then runs the sieve.
			int input = Integer.parseInt(num[0]);

			/* Beginning of Sieve of Eratosthenes */
			// start from 2
			OUTERLOOP:
				for (int i = 2; i <= input; i++) {
					// If the Clear button is pressed, cancels the process
					if(isCancelled()){
						primesOutput.setText("Canceled Before Completion");
					}

					// try to divide i by all known primes
					for (Integer p : listOfPrimes)
						if (i % p == 0)
							continue OUTERLOOP; // i is not prime
					// i is prime
					listOfPrimes.add(i);
				}
			/* End of Sieve of Eratosthenes */
			return listOfPrimes.toString();
		}

		// Displays the final list of primes found at the end of execution
		protected void onPostExecute(String result){
			// There are no primes for 0 and 1
			if(result.toString().equals("[]")){
				primesOutput.setText("None Found");
			}else{
				primesOutput.setText(result);
			}
			// Clears the progress bar
			progressBar.setIndeterminate(false);
			// Allows the buttons to be clicked again
			googleDriveButton.setClickable(true);
			graphPrimes.setClickable(true);
		}
	}

	/* The below code handles Google Drive integration */
	private void saveFileToDrive() {
		// Start by creating a new contents, and setting a callback.
		Log.i(TAG, "Creating new contents.");
		Drive.DriveApi.newContents(getGoogleApiClient()).setResultCallback(new ResultCallback<ContentsResult>() {

			@Override
			public void onResult(ContentsResult result) {
				// If the operation was not successful, we cannot do anything and must fail.
				if (!result.getStatus().isSuccess()) {
					Log.i(TAG, "Failed to create new contents.");
					return;
				}
				// Otherwise, we can write our data to the new contents.
				Log.i(TAG, "New contents created.");
				// Get an output stream for the contents.
				OutputStream outputStream = result.getContents().getOutputStream();
				// Write the bitmap data from it.
				try {
					outputStream.write(listOfPrimes.toString().getBytes());
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					Log.i(TAG, "Failed to write to Output Stream.");
					e1.printStackTrace();
				}
				// Create the initial metadata - MIME type and title.
				// Note that the user will be able to change the title later.
				MetadataChangeSet metadataChangeSet = new MetadataChangeSet.Builder()
				.setMimeType("text/html").setTitle("Prime Numbers.txt").build();
				// Create an intent for the file chooser, and start it.
				IntentSender intentSender = Drive.DriveApi
						.newCreateFileActivityBuilder()
						.setInitialMetadata(metadataChangeSet)
						.setInitialContents(result.getContents())
						.build(mGoogleApiClient);
				try {
					startIntentSenderForResult(
							intentSender, REQUEST_CODE_CREATOR, null, 0, 0, 0);
				} catch (SendIntentException e) {
					Log.i(TAG, "Failed to launch file chooser.");
				}
			}
		});
	}

	@Override
	protected void onStart() {
		super.onStart();
		getGoogleApiClient().connect();
	}

	public void showMessage(String message) {
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
	}

	public GoogleApiClient getGoogleApiClient() {
		return mGoogleApiClient;
	}

	@Override
	public void onConnected(Bundle connectionHint) {
		Log.i(TAG, "API client connected.");
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (mGoogleApiClient == null) {
			// Create the API client and bind it to an instance variable.
			// We use this instance as the callback for connection and connection failures.
			// Since no account name is passed, the user is prompted to choose.
			mGoogleApiClient = new GoogleApiClient.Builder(this)
			.addApi(Drive.API)
			.addScope(Drive.SCOPE_FILE)
			.addConnectionCallbacks(this)
			.addOnConnectionFailedListener(this)
			.build();
		}
		// Connect the client. Once connected, the camera is launched.
		mGoogleApiClient.connect();
	}

	@Override
	protected void onPause() {
		if (mGoogleApiClient != null) {
			mGoogleApiClient.disconnect();
		}
		super.onPause();
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		switch (requestCode) {
		case REQUEST_CODE_CREATOR:
			if (resultCode == RESULT_OK) {
				DriveId driveId = (DriveId) data.getParcelableExtra(
						OpenFileActivityBuilder.EXTRA_RESPONSE_DRIVE_ID);
				Log.i(TAG, "File created with ID: " + driveId);
				Toast.makeText(MainActivity.this, "File Uploaded!",
						Toast.LENGTH_SHORT).show();
			}
			break;
		default:
			super.onActivityResult(requestCode, resultCode, data);
			break;
		}
	}

	@Override
	public void onConnectionFailed(ConnectionResult result) {
		// Called whenever the API client fails to connect.
		Log.i(TAG, "GoogleApiClient connection failed: " + result.toString());
		if (!result.hasResolution()) {
			// show the localized error dialog.
			GooglePlayServicesUtil.getErrorDialog(result.getErrorCode(), this, 0).show();
			return;
		}
		// The failure has a resolution. Resolve it.
		// Called typically when the app is not yet authorized, and an
		// authorization dialog is displayed to the user.
		try {
			result.startResolutionForResult(this, REQUEST_CODE_RESOLUTION);
		} catch (SendIntentException e) {
			Log.e(TAG, "Exception while starting resolution activity", e);
		}
	}

	@Override
	public void onConnectionSuspended(int cause) {
		Log.i(TAG, "GoogleApiClient connection suspended");
	}

}
