package com.maxleap.example;

import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;

import com.maxleap.filesync.R;
import com.maxleap.filesync.SyncManager;

import java.io.File;
import java.io.IOException;

import as.leap.LASConfig;
import as.leap.LASUser;
import as.leap.LASUserManager;
import as.leap.callback.LogInCallback;
import as.leap.callback.SignUpCallback;
import as.leap.exception.LASException;


public class MainActivity extends ActionBarActivity {
    private SyncManager mSyncManager;
    private String mSyncRoot;

    private static final boolean DEBUG = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initMaxLeapService();
        register();
        login();
    }

    private void initMaxLeapService() {
        String applicationID = "5602069560b29edb434dd7ec";
        String restApiKey = "NzJnejdtYWRzNkpfOGhORkQ0MWNRQQ";
        LASConfig.initialize(this, applicationID, restApiKey);
    }

    private void register() {
        final String username = "filesync";
        final String password = "maxleapmobile";
        LASUser user = new LASUser();
        user.setUserName(username);
        user.setPassword(password);
        LASUserManager.signUpInBackground(user,
                new SignUpCallback() {
                    @Override
                    public void done(LASException e) {
                        log("=====signUpInBackground e : " + e);
                        LASUserManager.logInInBackground(username, password, new LogInCallback<LASUser>() {
                            @Override
                            public void done(LASUser lasUser, LASException e) {
                                log("=====logInInBackground e : " + e);
                            }
                        });
                    }
                });
    }

    private void login() {
        final String username = "filesync";
        final String password = "maxleapmobile";
        LASUserManager.logInInBackground(username, password, new LogInCallback<LASUser>() {
            @Override
            public void done(LASUser lasUser, LASException e) {
                log("=====logInInBackground lasUser : " + lasUser);
                log("=====logInInBackground e : " + e);
                test();
            }
        });
    }

    private void test() {
        configSync();
//        addFileTest();
//        clearAllTest();
//        addDirTest();
//        copyFileTest();
//        deleteFileTest();
//        moveFileTest();
        syncTest();
    }

    private void syncTest() {
        mSyncManager.startSync();
    }

    private void addDirTest() {
        File a = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/dir_02/");
        if (!a.exists()) {
            try {
                a.mkdirs();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        mSyncManager.addFile(a, new File(mSyncRoot + "/dir_02/"));
    }

    private void addFileTest() {
        File a = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/file_02.txt");
        if (!a.exists()) {
            try {
                a.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        mSyncManager.addFile(a, new File(mSyncRoot + "/file_02.txt"));
    }

    private void copyFileTest() {
        mSyncManager.copyFile(new File(mSyncRoot + "/file_01.txt"), new File(mSyncRoot + "/dir_01/file_01.txt"));
    }

    private void deleteFileTest() {
        mSyncManager.deleteFile(new File(mSyncRoot + "/dir_01/file_01.txt"));
    }

    private void clearAllTest() {
        mSyncManager.deleteFile(new File(mSyncRoot));
    }

    private void moveFileTest() {
        mSyncManager.moveFile(new File(mSyncRoot + "/dir_01/"), new File(mSyncRoot + "/dir_02/dir_01/"));
    }

    private void configSync() {
        mSyncManager = SyncManager.getInstance(this);
        mSyncRoot = Environment.getExternalStorageDirectory().getAbsolutePath() + "/SyncTest/";
        mSyncManager.setSyncRoot(mSyncRoot);
    }

    private void log(String content) {
        if (DEBUG) {
            Log.d("MainActivity", content);
        }
    }

}
