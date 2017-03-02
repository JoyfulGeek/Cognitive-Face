package com.mrpei.face;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AlertDialog;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.microsoft.projectoxford.face.FaceServiceClient;
import com.microsoft.projectoxford.face.contract.Face;
import com.microsoft.projectoxford.face.contract.IdentifyResult;
import com.microsoft.projectoxford.face.contract.TrainingStatus;
import com.mrpei.face.helper.ImageHelper;
import com.mrpei.face.helper.LogHelper;
import com.mrpei.face.helper.SampleApp;
import com.mrpei.face.helper.StorageHelper;
import com.mrpei.face.persongroupmanagement.PersonGroupListActivity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    // Background task of face detection.
    private class DetectionTask extends AsyncTask<InputStream, String, Face[]> {
        @Override
        protected Face[] doInBackground(InputStream... params) {
            // Get an instance of face service client to detect faces in image.
            FaceServiceClient faceServiceClient = SampleApp.getFaceServiceClient();
            try{
                publishProgress("Detecting...");

                // Start detection.
                return faceServiceClient.detect(
                        params[0],  /* Input stream of image to detect */
                        true,       /* Whether to return face ID */
                        false,       /* Whether to return face landmarks */
                        /* Which face attributes to analyze, currently we support:
                           age,gender,headPose,smile,facialHair */
                        null);
            }  catch (Exception e) {
                publishProgress(e.getMessage());
                return null;
            }
        }

        @Override
        protected void onPreExecute() {
            setUiBeforeBackgroundTask();
        }

        @Override
        protected void onProgressUpdate(String... values) {
            // Show the status of background detection task on screen.
            setUiDuringBackgroundTask(values[0]);
        }

        @Override
        protected void onPostExecute(Face[] result) {
            progressDialog.dismiss();

            setAllButtonsEnabledStatus(true);

            if (result != null) {
                // Set the adapter of the ListView which contains the details of detected faces.
                mFaceListAdapter = new FaceListAdapter(result);
                ListView listView = (ListView) findViewById(R.id.list_identified_faces);
                listView.setAdapter(mFaceListAdapter);

                if (result.length == 0) {
                    detected = false;
                    setInfo("No faces detected!");
                } else {
                    detected = true;
                    setInfo("Click on the \"Identify\" button to identify the faces in image.");
                }
            } else {
                detected = false;
            }

            refreshIdentifyButtonEnabledStatus();
        }
    }
    // Background task of face identification.
    private class IdentificationTask extends AsyncTask<UUID, String, IdentifyResult[]> {
        private boolean mSucceed = true;
        String mPersonGroupId;
        IdentificationTask(String personGroupId) {
            this.mPersonGroupId = personGroupId;
        }

        @Override
        protected IdentifyResult[] doInBackground(UUID... params) {
            String logString = "Request: Identifying faces ";
            for (UUID faceId: params) {
                logString += faceId.toString() + ", ";
            }
            logString += " in group " + mPersonGroupId;
            addLog(logString);

            // Get an instance of face service client to detect faces in image.
            FaceServiceClient faceServiceClient = SampleApp.getFaceServiceClient();
            try{
                publishProgress("Getting person group status...");

                TrainingStatus trainingStatus = faceServiceClient.getPersonGroupTrainingStatus(
                        this.mPersonGroupId);     /* personGroupId */

                if (trainingStatus.status != TrainingStatus.Status.Succeeded) {
                    publishProgress("Person group training status is " + trainingStatus.status);
                    mSucceed = false;
                    return null;
                }

                publishProgress("Identifying...");

                // Start identification.
                return faceServiceClient.identity(
                        this.mPersonGroupId,   /* personGroupId */
                        params,                  /* faceIds */
                        1);  /* maxNumOfCandidatesReturned */
            }  catch (Exception e) {
                mSucceed = false;
                publishProgress(e.getMessage());
                addLog(e.getMessage());
                return null;
            }
        }

        @Override
        protected void onPreExecute() {
            setUiBeforeBackgroundTask();
        }

        @Override
        protected void onProgressUpdate(String... values) {
            // Show the status of background detection task on screen.a
            setUiDuringBackgroundTask(values[0]);
        }

        @Override
        protected void onPostExecute(IdentifyResult[] result) {
            // Show the result on screen when detection is done.
            setUiAfterIdentification(result, mSucceed);
        }
    }

    // The adapter of the GridView which contains the details of the detected faces.
    private class FaceListAdapter extends BaseAdapter {
        // The detected faces.
        List<Face> faces;

        List<IdentifyResult> mIdentifyResults;

        // The thumbnails of detected faces.
        List<Bitmap> faceThumbnails;

        // Initialize with detection result.
        FaceListAdapter(Face[] detectionResult) {
            faces = new ArrayList<>();
            faceThumbnails = new ArrayList<>();
            mIdentifyResults = new ArrayList<>();

            if (detectionResult != null) {
                faces = Arrays.asList(detectionResult);
                for (Face face: faces) {
                    try {
                        // Crop face thumbnail with five main landmarks drawn from original image.
                        faceThumbnails.add(ImageHelper.generateFaceThumbnail(
                                mBitmap, face.faceRectangle));
                    } catch (IOException e) {
                        // Show the exception when generating face thumbnail fails.
                        setInfo(e.getMessage());
                    }
                }
            }
        }

        public void setIdentificationResult(IdentifyResult[] identifyResults) {
            mIdentifyResults = Arrays.asList(identifyResults);
        }

        @Override
        public boolean isEnabled(int position) {
            return false;
        }

        @Override
        public int getCount() {
            return faces.size();
        }

        @Override
        public Object getItem(int position) {
            return faces.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater layoutInflater =
                        (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = layoutInflater.inflate(
                        R.layout.item_face_with_description, parent, false);
            }
            convertView.setId(position);

            // Show the face thumbnail.
            ((ImageView)convertView.findViewById(R.id.face_thumbnail)).setImageBitmap(
                    faceThumbnails.get(position));

            if (mIdentifyResults.size() == faces.size()) {
                // Show the face details.
                DecimalFormat formatter = new DecimalFormat("#0.00");
                if (mIdentifyResults.get(position).candidates.size() > 0) {
                    String personId =
                            mIdentifyResults.get(position).candidates.get(0).personId.toString();
                    String personName = StorageHelper.getPersonName(
                            personId, mPersonGroupId, MainActivity.this);
                    String identity = "Person: " + personName + "\n"
                            + "Confidence: " + formatter.format(
                            mIdentifyResults.get(position).candidates.get(0).confidence);
                    ((TextView) convertView.findViewById(R.id.text_detected_face)).setText(
                            identity);
                } else {
                    ((TextView) convertView.findViewById(R.id.text_detected_face)).setText(
                            R.string.face_cannot_be_identified);
                }
            }

            return convertView;
        }
    }

    // The adapter of the ListView which contains the person groups.
    private class PersonGroupListAdapter extends BaseAdapter {
        List<String> personGroupIdList;

        // Initialize with detection result.
        PersonGroupListAdapter() {
            personGroupIdList = new ArrayList<>();

            Set<String> personGroupIds
                    = StorageHelper.getAllPersonGroupIds(MainActivity.this);

            for (String personGroupId: personGroupIds) {
                personGroupIdList.add(personGroupId);
                if (mPersonGroupId != null && personGroupId.equals(mPersonGroupId)) {
                    personGroupIdList.set(
                            personGroupIdList.size() - 1,
                            mPersonGroupListAdapter.personGroupIdList.get(0));
                    mPersonGroupListAdapter.personGroupIdList.set(0, personGroupId);
                }
            }
        }

        @Override
        public int getCount() {
            return personGroupIdList.size();
        }

        @Override
        public Object getItem(int position) {
            return personGroupIdList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater layoutInflater =
                        (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = layoutInflater.inflate(R.layout.item_person_group, parent, false);
            }
            convertView.setId(position);

            // set the text of the item
            String personGroupName = StorageHelper.getPersonGroupName(
                    personGroupIdList.get(position), MainActivity.this);
            int personNumberInGroup = StorageHelper.getAllPersonIds(
                    personGroupIdList.get(position), MainActivity.this).size();
            ((TextView)convertView.findViewById(R.id.text_person_group)).setText(
                    String.format(
                            "%s (成员数: %d)",
                            personGroupName,
                            personNumberInGroup));

            if (position == 0) {
                ((TextView)convertView.findViewById(R.id.text_person_group)).setTextColor(
                        Color.parseColor("#3399FF"));
            }

            return convertView;
        }
    }

    String mPersonGroupId;
    boolean detected;
    FaceListAdapter mFaceListAdapter;
    PersonGroupListAdapter mPersonGroupListAdapter;

    // Flag to indicate which task is to be performed.
    private static final int REQUEST_SELECT_IMAGE = 0;
    private Uri mUriPhotoTaken;
    // The image selected to detect.
    private Bitmap mBitmap;
    // Progress dialog popped up when communicating with server.
    ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        判断key
        if (getString(R.string.subscription_key).startsWith("Please")) {
            AlertDialog show = new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.add_subscription_key_tip_title))
                    .setMessage(getString(R.string.add_subscription_key_tip))
                    .setCancelable(false)
                    .show();
        }
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        detected = false;

        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle(getString(R.string.progress_dialog_title));

        LogHelper.clearIdentificationLog();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.camera) {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(MainActivity.this);
            final String[] cities = { "拍摄","从相册选择"};

            builder.setItems(cities , new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface arg0, int arg1) {
                    if (arg1 == 1) {
                        selectImageInAlbum();
                    }
                    if (arg1 == 0){
                        takePhoto();
                    }
                    arg0.dismiss();
                }
            });
            builder.show();
        }

        return super.onOptionsItemSelected(item);
    }
    //从相册选择图片
    public void selectImageInAlbum() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent,REQUEST_SELECT_IMAGE );
        }
    }

    //拍摄并返回
    public void takePhoto() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if(intent.resolveActivity(getPackageManager()) != null) {
            // Save the photo taken to a temporary file.临时存储
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            try {
                File file = File.createTempFile("IMG_", ".jpg", storageDir);
                mUriPhotoTaken = Uri.fromFile(file);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, mUriPhotoTaken);
                startActivityForResult(intent, REQUEST_SELECT_IMAGE);
            } catch (IOException e) {
                setInfo(e.getMessage());
            }
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_camera) {

        } else if (id == R.id.nav_gallery) {
            Intent intent = new Intent(this, PersonGroupListActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_manage) {

        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {
            Intent intent = new Intent(this, TestActivity.class);
            startActivity(intent);
        }
//        else if (id == R.id.nav_slideshow) {
//
//        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }


    //主功能
    @Override
    protected void onResume() {
        super.onResume();

        ListView listView = (ListView) findViewById(R.id.list_person_groups_identify);
        mPersonGroupListAdapter = new PersonGroupListAdapter();
        listView.setAdapter(mPersonGroupListAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                setPersonGroupSelected(position);
            }
        });

        if (mPersonGroupListAdapter.personGroupIdList.size() != 0) {
            setPersonGroupSelected(0);
        } else {
            setPersonGroupSelected(-1);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode)
        {
            case REQUEST_SELECT_IMAGE:
                if(resultCode == RESULT_OK) {
                    detected = false;

                    // If image is selected successfully, set the image URI and bitmap.
                    Uri imageUri ;
                    if(data == null || data.getData() ==null) {
                        imageUri = mUriPhotoTaken;
                    }else {
                        imageUri  = data.getData();
                    }
                    mBitmap = ImageHelper.loadSizeLimitedBitmapFromUri(
                            imageUri, getContentResolver());
                    if (mBitmap != null) {
                        // Show the image on screen.
//                        ImageView imageView = (ImageView) findViewById(R.id.image);
//                        imageView.setImageBitmap(mBitmap);
                    }

                    // Clear the identification result.
                    FaceListAdapter faceListAdapter = new FaceListAdapter(null);
                    ListView listView = (ListView) findViewById(R.id.list_identified_faces);
                    listView.setAdapter(faceListAdapter);

                    // Clear the information panel.
                    setInfo("");

                    // Start detecting in image.
                    detect(mBitmap);
                }
                break;
            default:
                break;
        }
    }

    void setPersonGroupSelected(int position) {
        TextView textView = (TextView) findViewById(R.id.text_person_group_selected);
        if (position > 0) {
            String personGroupIdSelected = mPersonGroupListAdapter.personGroupIdList.get(position);
            mPersonGroupListAdapter.personGroupIdList.set(
                    position, mPersonGroupListAdapter.personGroupIdList.get(0));
            mPersonGroupListAdapter.personGroupIdList.set(0, personGroupIdSelected);
            ListView listView = (ListView) findViewById(R.id.list_person_groups_identify);
            listView.setAdapter(mPersonGroupListAdapter);
            setPersonGroupSelected(0);
        } else if (position < 0) {
            setIdentifyButtonEnabledStatus(false);
            textView.setTextColor(Color.RED);
            textView.setText(R.string.no_person_group_selected_for_identification_warning);
        } else {
            mPersonGroupId = mPersonGroupListAdapter.personGroupIdList.get(0);
            String personGroupName = StorageHelper.getPersonGroupName(
                    mPersonGroupId, MainActivity.this);
            refreshIdentifyButtonEnabledStatus();
            textView.setTextColor(Color.BLACK);
            textView.setText(String.format("确定选择的群组: %s", personGroupName));
        }
    }

    private void setUiBeforeBackgroundTask() {
        progressDialog.show();
    }

    // Show the status of background detection task on screen.
    private void setUiDuringBackgroundTask(String progress) {
        progressDialog.setMessage(progress);

        setInfo(progress);
    }

    // Show the result on screen when detection is done.
    private void setUiAfterIdentification(IdentifyResult[] result, boolean succeed) {
        progressDialog.dismiss();

        setAllButtonsEnabledStatus(true);
        setIdentifyButtonEnabledStatus(false);

        if (succeed) {
            // Set the information about the detection result.
            setInfo("Identification is done");

            if (result != null) {
                mFaceListAdapter.setIdentificationResult(result);

                String logString = "Response: Success. ";
                for (IdentifyResult identifyResult: result) {
                    logString += "Face " + identifyResult.faceId.toString() + " is identified as "
                            + (identifyResult.candidates.size() > 0
                            ? identifyResult.candidates.get(0).personId.toString()
                            : "Unknown Person")
                            + ". ";
                }
                addLog(logString);

                // Show the detailed list of detected faces.
                ListView listView = (ListView) findViewById(R.id.list_identified_faces);
                listView.setAdapter(mFaceListAdapter);

                TextView textView = (TextView) findViewById(R.id.tips);
                textView.setText("检测结果");
            }
        }
    }

    // Start detecting in image.
    private void detect(Bitmap bitmap) {
        // Put the image into an input stream for detection.
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(output.toByteArray());

        setAllButtonsEnabledStatus(false);

        // Start a background task to detect faces in the image.
        new DetectionTask().execute(inputStream);
    }

    // Called when the "Identify" button is clicked.
    public void identify(View view) {
        // Start detection task only if the image to detect is selected.
        if (detected && mPersonGroupId != null) {
            // Start a background task to identify faces in the image.
            List<UUID> faceIds = new ArrayList<>();
            for (Face face:  mFaceListAdapter.faces) {
                faceIds.add(face.faceId);
            }

            setAllButtonsEnabledStatus(false);

            new IdentificationTask(mPersonGroupId).execute(
                    faceIds.toArray(new UUID[faceIds.size()]));
        } else {
            // Not detected or person group exists.
            setInfo("Please select an image and create a person group first.");
        }
    }

    public void managePersonGroups(View view) {
        Intent intent = new Intent(this, PersonGroupListActivity.class);
        startActivity(intent);

        refreshIdentifyButtonEnabledStatus();
    }

    // Add a log item.
    private void addLog(String log) {
        LogHelper.addIdentificationLog(log);
    }

    // Set whether the buttons are enabled.
    private void setAllButtonsEnabledStatus(boolean isEnabled) {
        Button identifyButton = (Button) findViewById(R.id.identify);
        identifyButton.setEnabled(isEnabled);

    }

    // Set the group button is enabled or not.
    private void setIdentifyButtonEnabledStatus(boolean isEnabled) {
        Button button = (Button) findViewById(R.id.identify);
        button.setEnabled(isEnabled);
    }

    // Set the group button is enabled or not.
    private void refreshIdentifyButtonEnabledStatus() {
        if (detected && mPersonGroupId != null) {
            setIdentifyButtonEnabledStatus(true);
        } else {
            setIdentifyButtonEnabledStatus(false);
        }
    }

    // Set the information panel on screen.
    private void setInfo(String info) {
//        TextView textView = (TextView) findViewById(R.id.info);
//        textView.setText(info);
    }

}
