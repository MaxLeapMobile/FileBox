package com.maxleap.filesync;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import as.leap.LASPrivateFile;
import as.leap.LASPrivateFileManager;
import as.leap.callback.DeleteCallback;
import as.leap.callback.DownloadCallback;
import as.leap.callback.GetMetaDataCallback;
import as.leap.callback.ProgressCallback;
import as.leap.callback.SaveCallback;
import as.leap.exception.LASException;

public class SyncManager {

    public static final int ACTION_GET_FILE = 0;
    public static final int ACTION_COPY_FILE = 1;
    public static final int ACTION_MOVE_FILE = 2;
    public static final int ACTION_DELETE_FILE = 3;
    public static final int ACTION_CREATE_REMOTE_FOLDER = 4;
    public static final int ACTION_DOWNLOAD_FILE = 5;
    public static final int ACTION_UPLOAD_FILE = 6;
    public static final int ACTION_CREATE_LOCAL_FOLDER = 7;

    private static final String SP_FILE_SYNC = "file_sync";
    private static final String SP_FILE_SYNC_ROOT = "file_sync_root";

    private LinkedList<ActionItem> mActionItems;
    private DBHelper mDBHelper;

    private HandlerThread mThread;
    private Context mContext;

    private static SyncManager instance;
    private Handler mHandler;
    private int what;
    private String mSyncRoot;

    private static final boolean DEBUG = true;

    private SyncManager(Context context) {
        this.mContext = context;
        this.what = 0;
        SharedPreferences share = context.getSharedPreferences(SP_FILE_SYNC, 0);
        mSyncRoot = share.getString(SP_FILE_SYNC_ROOT, null);
        mDBHelper = DBHelper.getInstance(context);

        mThread = new HandlerThread("ActionHandleThread");
        mThread.start();
        mHandler = new Handler(mThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                loopAction();
            }
        };
    }

    public static SyncManager getInstance(Context context) {
        if (instance == null) {
            instance = new SyncManager(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * Setup sync root
     *
     * @param syncRoot
     */
    public void setSyncRoot(String syncRoot) {
        if (TextUtils.isEmpty(syncRoot)) {
            throw new IllegalArgumentException("The Sync Root should not be null.");
        }
        File dir = new File(syncRoot);
        if (!dir.exists()) {
            dir.mkdirs();
        } else {
            if (!dir.isDirectory()) {
                throw new IllegalArgumentException("The Sync Root should be a directory.");
            }
        }

        mSyncRoot = dir.getAbsolutePath();
        SharedPreferences share = mContext.getSharedPreferences(SP_FILE_SYNC,
                Activity.MODE_PRIVATE);
        share.edit().putString(SP_FILE_SYNC_ROOT, mSyncRoot).apply();
    }

    /**
     * Add file into Cloud. Keep the src and target same if the file is already under the syncRoot.
     *
     * @param src    the File to be added
     * @param target the File where to be put
     */
    public void addFile(final File src, final File target) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!src.exists()) throw new IllegalArgumentException("src not exist!");
                checkTarget(target);
                if (src.getAbsolutePath().equals(target.getAbsolutePath())) {
                    if (mDBHelper.getFileItemByPath(src.getAbsolutePath()) == null) {
                        addFileToDB(target);
                        syncFileAdd(target);
                    }
                } else {
                    copyFileToSDCard(src, target);
                    addFileToDB(target);
                    syncFileAdd(target);
                }
            }
        });

    }

    /**
     * @param file
     */
    public void deleteFile(final File file) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                checkSrcInSyncRoot(file);
                deleteFileFromDB(file);
                deleteFileFromSDCard(file);
                syncFileDelete(file);
            }
        });
    }

    /**
     * @param src
     * @param target
     */
    public void renameFile(File src, File target) {
        moveFile(src, target);
    }

    /**
     * @param src
     * @param target
     */
    public void copyFile(final File src, final File target) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                checkTarget(target);
                checkSrcInSyncRoot(src);
                copyFileToSDCard(src, target);
                addFileToDB(target);
                syncFileCopy(src, target);
            }
        });
    }

    /**
     * @param src
     * @param target
     */
    public void moveFile(final File src, final File target) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                checkTarget(target);
                checkSrcInSyncRoot(src);

                copyFileToSDCard(src, target);
                deleteFileFromDB(src);
                deleteFileFromSDCard(src);
                addFileToDB(target);
                syncFileMove(src, target);
            }
        });
    }

    /**
     * Get all ActionItems
     *
     * @return all ActionItems
     */
    public LinkedList<ActionItem> getAllActionItems() {
        return mDBHelper.getAllActionItems();
    }

    private void addFileToDB(File file) {
        FileItem item = new FileItem();
        item.setFilename(file.getName());
        item.setPath(file.getAbsolutePath());
        item.setCreateTime(System.currentTimeMillis());

        if (file.isDirectory()) {
            item.setIsFolder(true);
            mDBHelper.insert(item);

            String[] children = file.list();
            for (int i = 0; i < file.listFiles().length; i++) {
                addFileToDB(new File(file, children[i]));
            }
        } else {
            item.setIsFolder(false);
            mDBHelper.insert(item);
        }
    }

    private void deleteFileFromDB(File file) {
        if (file.isDirectory()) {
            FileItem item = mDBHelper.getFileItemByPath(file.getAbsolutePath());
            if (item != null) {
                mDBHelper.delete(item);
            }

            String[] children = file.list();
            for (int i = 0; i < file.listFiles().length; i++) {
                deleteFileFromDB(new File(file, children[i]));
            }
        } else {
            FileItem item = mDBHelper.getFileItemByPath(file.getAbsolutePath());
            if (item != null) {
                mDBHelper.delete(item);
            }
        }
    }

    private void syncFileAdd(File file) {
        log("=====syncFileAdd file : " + file.getAbsolutePath());
        if (file.isDirectory()) {
            ActionItem actionItem = new ActionItem();
            actionItem.setType(ACTION_CREATE_REMOTE_FOLDER);
            actionItem.setCreateTime(System.currentTimeMillis());
            actionItem.setLocalPath(file.getAbsolutePath());
            actionItem.setRemotePath(file.getAbsolutePath().replace(mSyncRoot, ""));
            addActionItem(actionItem);

            String[] children = file.list();
            for (int i = 0; i < file.listFiles().length; i++) {
                syncFileAdd(new File(file, children[i]));
            }
        } else {
            ActionItem actionItem = new ActionItem();
            actionItem.setType(ACTION_UPLOAD_FILE);
            actionItem.setCreateTime(file.lastModified());
            actionItem.setLocalPath(file.getAbsolutePath());
            actionItem.setRemotePath(file.getAbsolutePath().replace(mSyncRoot, ""));
            addActionItem(actionItem);
        }
    }

    private void syncFileCopy(File src, File target) {
        ActionItem actionItem = new ActionItem();
        actionItem.setType(ACTION_COPY_FILE);
        actionItem.setCreateTime(System.currentTimeMillis());
        actionItem.setLocalPath(src.getAbsolutePath());
        actionItem.setRemotePath(src.getAbsolutePath().replace(mSyncRoot, ""));
        actionItem.setMisc(target.getAbsolutePath().replace(mSyncRoot, ""));
        addActionItem(actionItem);
    }

    private void syncFileDelete(File file) {
        ActionItem actionItem = new ActionItem();
        actionItem.setType(ACTION_DELETE_FILE);
        actionItem.setCreateTime(System.currentTimeMillis());
        actionItem.setLocalPath(file.getAbsolutePath());
        actionItem.setRemotePath(file.getAbsolutePath().replace(mSyncRoot, ""));
        addActionItem(actionItem);
    }

    private void syncFileMove(File src, File target) {
        ActionItem actionItem = new ActionItem();
        actionItem.setType(ACTION_MOVE_FILE);
        actionItem.setCreateTime(System.currentTimeMillis());
        actionItem.setLocalPath(src.getAbsolutePath());
        actionItem.setRemotePath(src.getAbsolutePath().replace(mSyncRoot, ""));
        actionItem.setMisc(target.getAbsolutePath().replace(mSyncRoot, ""));
        addActionItem(actionItem);
    }

    private void checkSrcInSyncRoot(File src) {
        if (!src.exists()) {
            throw new IllegalArgumentException("src not exist!");
        }
        if (mSyncRoot == null) {
            throw new IllegalStateException("You have not set Sync Root yet.");
        }
        if (!src.getAbsolutePath().startsWith(mSyncRoot)) {
            throw new IllegalArgumentException("The src should start with Sync Root when invoke copy, rename,move or delete.");
        }
    }

    private void checkTarget(File target) {
        if (mSyncRoot == null) {
            throw new IllegalStateException("You have not set Sync Root yet.");
        }
        if (!target.getAbsolutePath().startsWith(mSyncRoot)) {
            throw new IllegalArgumentException("The target should start with Sync Root");
        }
    }

    private void deleteFileFromSDCard(File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                deleteFileFromSDCard(child);
            }
        }
        file.delete();
    }

    private void copyFileToSDCard(File src, File target) {
        if (src.isDirectory()) {
            if (!target.exists()) {
                target.mkdirs();
            }
            String[] children = src.list();
            for (int i = 0; i < src.listFiles().length; i++) {
                copyFileToSDCard(new File(src, children[i]),
                        new File(target, children[i]));
            }
        } else {
            InputStream in = null;
            OutputStream out = null;
            try {
                in = new FileInputStream(src);
                out = new FileOutputStream(target);
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (in != null) {
                        in.close();
                    }
                    if (out != null) {
                        out.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void loopAction() {
        log("=====loopAction Thread id : " + Thread.currentThread().getId());
        log("=====loopAction mActionItems : " + mActionItems);
        if (mActionItems == null || mActionItems.isEmpty()) return;
        log("=====loopAction mActionItems.size() : " + mActionItems.size());
        ActionItem actionItem = mActionItems.get(0);
        switch (actionItem.getType()) {
            case ACTION_GET_FILE:
                getFile(actionItem);
                break;
            case ACTION_COPY_FILE:
                copyFile(actionItem);
                break;
            case ACTION_MOVE_FILE:
                moveFile(actionItem);
                break;
            case ACTION_DELETE_FILE:
                deleteFile(actionItem);
                break;
            case ACTION_CREATE_REMOTE_FOLDER:
                createRemoteFolder(actionItem);
                break;
            case ACTION_DOWNLOAD_FILE:
                downloadFile(actionItem);
                break;
            case ACTION_UPLOAD_FILE:
                uploadFile(actionItem);
                break;
            case ACTION_CREATE_LOCAL_FOLDER:
                createLocalFolder(actionItem);
                break;
            default:
                break;
        }
    }

    public void startSync() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mActionItems != null && mActionItems.size() > 0) {
                    return;
                }
                if (mActionItems == null) {
                    mActionItems = mDBHelper.getUnprocessedActionItems();
                }
                if (mActionItems.size() == 0) {
                    ActionItem actionItem = new ActionItem();
                    actionItem.setType(ACTION_GET_FILE);
                    actionItem.setLocalPath(mSyncRoot);
                    actionItem.setRemotePath(File.separator);
                    addActionItem(actionItem);
                }
            }
        });
    }

    private void addActionItem(final ActionItem actionItem) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                log("=====addActionItem Thread id : " + Thread.currentThread().getId());
                log("=====addActionItem mActionItems : " + mActionItems);
                if (mActionItems == null) {
                    mActionItems = mDBHelper.getUnprocessedActionItems();
                }
                log("=====addActionItem mActionItems.size() : " + mActionItems.size());
                mActionItems.add(actionItem);
                mDBHelper.insert(actionItem);
                mHandler.sendEmptyMessage(what);
            }
        });

    }

    /**
     * @param isSuccess
     */
    private void removeActionItem(final boolean isSuccess) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                log("=====removeActionItem");
                if (mActionItems.size() == 0) return;
                ActionItem actionItem = mActionItems.get(0);
                actionItem.setState(isSuccess ? DBHelper.SyncAction.STATE_SUCCESS : DBHelper.SyncAction.STATE_FAILED);
                mDBHelper.update(mActionItems.get(0));
                mActionItems.remove(0);
                log("=====removeActionItem mActionItems.size() : " + mActionItems.size());
                mHandler.sendEmptyMessage(what);
            }
        });
    }


    private void getFile(ActionItem actionItem) {
        log("=====getFile");
        final String RemotePath = actionItem.getRemotePath();
        LASPrivateFile privateFile = LASPrivateFile.createDirectory(RemotePath);
        LASPrivateFileManager.getMetaDataInBackground(privateFile, true, new GetMetaDataCallback() {
                    @Override
                    public void done(LASPrivateFile lasPrivateFile, LASException e) {
                        log("=====getFile e:" + e);
                        log("=====getFile Thread id : " + Thread.currentThread().getId());
                        if (e == null) {
                            ArrayList<FileItem> fileItems = mDBHelper.getFileItemsByParentPath(mSyncRoot.concat(RemotePath));
                            List<LASPrivateFile> files = lasPrivateFile.getChildren();
                            if (files != null) {
                                for (LASPrivateFile file : files) {
                                    boolean hasChecked = false;
                                    for (int i = 0; i < fileItems.size(); i++) {
                                        // local exist and remote exist
                                        if (file.isDirectory()) {
                                            if (fileItems.get(i).isFolder() &&
                                                    mSyncRoot.concat(file.getRemotePath())
                                                            .equals(fileItems.get(i).getPath())) {
                                                ActionItem actionItem = new ActionItem();
                                                actionItem.setType(ACTION_GET_FILE);
                                                actionItem.setLocalPath(mSyncRoot.concat(file.getRemotePath()));
                                                actionItem.setRemotePath(file.getRemotePath());
                                                actionItem.setCreateTime(System.currentTimeMillis());
                                                addActionItem(actionItem);
                                                hasChecked = true;
                                                fileItems.remove(i);
                                                break;
                                            }
                                        } else {
                                            if (!fileItems.get(i).isFolder() &&
                                                    file.getHash().equals(fileItems.get(i).getHashValue())) {
                                                hasChecked = true;
                                                fileItems.remove(i);
                                                break;
                                            }
                                        }
                                    }
                                    // local unexist and remote exist
                                    if (!hasChecked) {
                                        ActionItem actionItem = new ActionItem();
                                        if (file.isDirectory()) {
                                            actionItem.setType(ACTION_CREATE_LOCAL_FOLDER);
                                            actionItem.setLocalPath(mSyncRoot.concat(file.getRemotePath()));
                                            actionItem.setRemotePath(file.getRemotePath());
                                            actionItem.setCreateTime(System.currentTimeMillis());
                                        } else {
                                            actionItem.setType(ACTION_DOWNLOAD_FILE);
                                            actionItem.setMisc(file.getHash());
                                            actionItem.setLocalPath(mSyncRoot.concat(file.getRemotePath()));
                                            actionItem.setRemotePath(file.getRemotePath());
                                            actionItem.setCreateTime(System.currentTimeMillis());
                                        }
                                        addActionItem(actionItem);
                                    }
                                }
                                // local exist and remote unexist
                                for (int i = 0; i < fileItems.size(); i++) {
                                    ActionItem actionItem = new ActionItem();
                                    FileItem fileItem = fileItems.get(i);
                                    if (fileItem.isFolder()) {
                                        if (fileItem.getHashValue() == DBHelper.SyncFile.FOLDER_SYNCED_HASH_VALUE) {
                                            deleteFileFromDB(new File(fileItem.getPath()));
                                        } else {
                                            actionItem.setType(ACTION_CREATE_REMOTE_FOLDER);
                                        }
                                    } else {
                                        if (TextUtils.isEmpty(fileItem.getHashValue())) {
                                            actionItem.setType(ACTION_UPLOAD_FILE);
                                        } else {
                                            deleteFileFromDB(new File(fileItem.getPath()));
                                        }
                                    }
                                    actionItem.setLocalPath(fileItem.getPath());
                                    actionItem.setRemotePath(fileItem.getPath().replace(mSyncRoot, ""));
                                    actionItem.setCreateTime(System.currentTimeMillis());
                                    addActionItem(actionItem);
                                }
                                removeActionItem(true);
                            }
                        } else if (e.getCode() == LASException.PATH_NOT_EXIST) {
                            removeActionItem(false);
                        } else {
                            removeActionItem(false);
                        }
                    }
                }

        );
    }

    /**
     * Create Folder on Cloud
     *
     * @param actionItem
     */
    private void createRemoteFolder(final ActionItem actionItem) {
        log("=====createRemoteFolder");
        LASPrivateFile privateFile = LASPrivateFile.createDirectory(actionItem.getRemotePath());
        LASPrivateFileManager.createDirectoryInBackground(privateFile, new SaveCallback() {
            @Override
            public void done(LASException e) {
                log("=====createDirectoryInBackground e : " + e);
                if (e == null) {
                    syncFileAdd(new File(actionItem.getLocalPath()));
                    removeActionItem(true);
                } else {
                    removeActionItem(false);
                }
            }
        });
    }

    private void downloadFile(final ActionItem actionItem) {
        log("=====downloadFile");
        final LASPrivateFile privateFile = LASPrivateFile.createFile(actionItem.getRemotePath());
        File file = new File(actionItem.getLocalPath());
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            if (!file.exists()) {
                file.createNewFile();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        LASPrivateFileManager.getDataWithPathInBackground(privateFile, actionItem.getLocalPath(), new DownloadCallback() {
            @Override
            public void done(String s, LASException e) {
                log("=====downloadFile e : " + e);
                if (e == null) {
                    FileItem fileItem = mDBHelper.getFileItemByPath(actionItem.getLocalPath());
                    fileItem.setHashValue(actionItem.getMisc());
                    mDBHelper.update(fileItem);
                    removeActionItem(true);
                } else if (e.getCode() == LASException.PATH_NOT_EXIST) {
                    removeActionItem(false);
                } else {
                    removeActionItem(false);
                }
            }
        }, new ProgressCallback() {
            @Override
            public void done(int i) {
            }
        });
    }

    private void uploadFile(final ActionItem actionItem) {
        log("=====uploadFile");
        if (!new File(actionItem.getLocalPath()).exists()) {
            removeActionItem(false);
        }
        final LASPrivateFile privateFile = LASPrivateFile.createFile(actionItem.getLocalPath(), actionItem.getRemotePath());
        LASPrivateFileManager.saveInBackground(privateFile, false, new SaveCallback() {
            @Override
            public void done(LASException e) {
                log("=====uploadFile e:" + e);
                if (e == null) {
                    FileItem fileItem = mDBHelper.getFileItemByPath(actionItem.getLocalPath());
                    fileItem.setHashValue(actionItem.getMisc());
                    mDBHelper.update(fileItem);
                    removeActionItem(true);
                } else if (e.getCode() == LASException.PATH_NOT_EXIST) {
                    removeActionItem(false);
                } else {
                    removeActionItem(false);
                }
            }
        }, null);
    }

    private void createLocalFolder(ActionItem actionItem) {
        File file = new File(actionItem.getLocalPath());
        if (!file.exists()) {
            file.mkdirs();
        }
        FileItem item = new FileItem();
        item.setFilename(file.getName());
        item.setPath(file.getAbsolutePath());
        item.setIsFolder(true);
        item.setHashValue(DBHelper.SyncFile.FOLDER_SYNCED_HASH_VALUE);
        item.setCreateTime(System.currentTimeMillis());
        mDBHelper.insert(item);

        ActionItem newActionItem = new ActionItem();
        newActionItem.setType(ACTION_GET_FILE);
        newActionItem.setLocalPath(file.getAbsolutePath());
        newActionItem.setRemotePath(file.getAbsolutePath().replace(mSyncRoot, ""));
        newActionItem.setCreateTime(System.currentTimeMillis());
        addActionItem(newActionItem);
        removeActionItem(true);
    }

    private void deleteFile(ActionItem actionItem) {
        log("=====deleteFile");
        LASPrivateFile privateFile = LASPrivateFile.createFile(actionItem.getRemotePath());
        if (actionItem.getRemotePath().isEmpty()) {
            privateFile = LASPrivateFile.createFile(File.separator);
        }
        LASPrivateFileManager.deleteInBackground(privateFile, new DeleteCallback() {
            @Override
            public void done(LASException e) {
                log("=====deleteFile e:" + e);
                if (e == null || e.getCode() == LASException.PATH_NOT_EXIST) {
                    removeActionItem(true);
                } else {
                    removeActionItem(false);
                }
            }
        });
    }

    private void copyFile(ActionItem actionItem) {
        log("=====copyFile");
        LASPrivateFile fromPrivateFile = LASPrivateFile.createFile(actionItem.getRemotePath());
        LASPrivateFile toPrivateFile = LASPrivateFile.createFile(actionItem.getMisc());
        LASPrivateFileManager.copyInBackground(fromPrivateFile, toPrivateFile, false, new SaveCallback() {
            @Override
            public void done(LASException e) {
                log("=====copyFile e:" + e);
                if (e == null || e.getCode() == LASException.PATH_NOT_EXIST) {
                    removeActionItem(true);
                } else {
                    removeActionItem(false);
                }
            }
        });
    }

    private void moveFile(ActionItem actionItem) {
        log("=====moveFile");
        LASPrivateFile fromPrivateFile = LASPrivateFile.createFile(actionItem.getRemotePath());
        LASPrivateFile toPrivateFile = LASPrivateFile.createFile(actionItem.getMisc());
        LASPrivateFileManager.moveInBackground(fromPrivateFile, toPrivateFile, false, new SaveCallback() {
            @Override
            public void done(LASException e) {
                log("=====moveFile e:" + e);
                if (e == null || e.getCode() == LASException.PATH_NOT_EXIST) {
                    removeActionItem(true);
                } else {
                    removeActionItem(false);
                }
            }
        });
    }

    private void log(String content) {
        if (DEBUG) {
            Log.d("SyncManager", content);
        }
    }

}
