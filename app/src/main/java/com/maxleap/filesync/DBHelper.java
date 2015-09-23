package com.maxleap.filesync;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;

public class DBHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "FileSyncDB";
    private static final int DB_VERSION = 1;
    private static DBHelper mInstance;

    private int countOpenTimes;
    private SQLiteDatabase mDatabase;

    public DBHelper(Context context, String name, SQLiteDatabase.CursorFactory factory,
                    int version) {
        super(context, name, factory, version);
    }

    private DBHelper(Context context) {
        this(context, DB_NAME, null, DB_VERSION);
    }

    public class SyncFile {

        public static final String TABLE_NAME = "sync_file";

        public static final int BASE_FOLDER_ID = 0;
        public static final String FOLDER_SYNCED_HASH_VALUE = "d354f24e5d9b72df39f39243c80d9d80";

        public class Columns {
            public static final String ID = "id";
            public static final String FILENAME = "filename";
            public static final String PATH = "path";
            public static final String IS_FOLDER = "is_folder";
            public static final String PARENT_ID = "parent_id";
            public static final String CREATE_TIME = "create_time";
            public static final String HASH_VALUE = "hash_value";
        }
    }

    public class SyncAction {

        public static final String TABLE_NAME = "sync_action";

        public static final int STATE_DEFAULT = 0;
        public static final int STATE_SUCCESS = 1;
        public static final int STATE_FAILED = 2;

        public class Columns {
            public static final String ID = "id";
            public static final String TYPE = "type";
            public static final String REMOTE_PATH = "remote_path";
            public static final String LOCAL_PATH = "local_path";
            public static final String CREATE_TIME = "create_time";
            public static final String STATE = "state";
            public static final String MISC = "misc";
        }
    }

    public synchronized static DBHelper getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new DBHelper(context);
        }
        return mInstance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + SyncFile.TABLE_NAME + "("
                + SyncFile.Columns.ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + SyncFile.Columns.FILENAME + " TEXT,"
                + SyncFile.Columns.PATH + " TEXT,"
                + SyncFile.Columns.IS_FOLDER + " INTEGER,"
                + SyncFile.Columns.HASH_VALUE + " VARCHAR(32),"
                + SyncFile.Columns.PARENT_ID + " INTEGER DEFAULT " + SyncFile.BASE_FOLDER_ID + ","
                + SyncFile.Columns.CREATE_TIME + " VARCHAR(16))");

        db.execSQL("CREATE TABLE " + SyncAction.TABLE_NAME + "("
                + SyncAction.Columns.ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + SyncAction.Columns.TYPE + " INTEGER,"
                + SyncAction.Columns.LOCAL_PATH + " TEXT,"
                + SyncAction.Columns.REMOTE_PATH + " TEXT,"
                + SyncAction.Columns.CREATE_TIME + " VARCHAR(16),"
                + SyncAction.Columns.STATE + " INTEGER DEFAULT " + SyncAction.STATE_DEFAULT + ","
                + SyncAction.Columns.MISC + " TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    /**
     * Insert Item into Database
     *
     * @param object The item will be created.
     * @return -1 if there is an error
     */
    protected long insert(Object object) {

        long id = -1;
        SQLiteDatabase db = openDatabase();

        if (object instanceof FileItem) {
            ContentValues cv = new ContentValues();
            String filename = ((FileItem) object).getFilename();
            String path = ((FileItem) object).getPath();
            String parentFolder = path.substring(0, path.lastIndexOf(File.separator.concat(filename)));
            FileItem fileItem = this.getFileItemByPath(parentFolder);

            cv.put(SyncFile.Columns.FILENAME, filename);
            cv.put(SyncFile.Columns.PATH, path);
            cv.put(SyncFile.Columns.IS_FOLDER, ((FileItem) object).isFolder());
            if(fileItem != null){
                cv.put(SyncFile.Columns.PARENT_ID, fileItem.getId());
            }else {
                cv.put(SyncFile.Columns.PARENT_ID, SyncFile.BASE_FOLDER_ID);
            }
            cv.put(SyncFile.Columns.CREATE_TIME, ((FileItem) object).getCreateTime());
            cv.put(SyncFile.Columns.HASH_VALUE, ((FileItem) object).getHashValue());
            id = db.insert(SyncFile.TABLE_NAME, null, cv);
        } else if (object instanceof ActionItem) {
            ContentValues cv = new ContentValues();
            cv.put(SyncAction.Columns.TYPE, ((ActionItem) object).getType());
            cv.put(SyncAction.Columns.REMOTE_PATH, ((ActionItem) object).getRemotePath());
            cv.put(SyncAction.Columns.LOCAL_PATH, ((ActionItem) object).getLocalPath());
            cv.put(SyncAction.Columns.CREATE_TIME, ((ActionItem) object).getCreateTime());
            cv.put(SyncAction.Columns.STATE, ((ActionItem) object).getState());
            cv.put(SyncAction.Columns.MISC, ((ActionItem) object).getMisc());
            id = db.insert(SyncAction.TABLE_NAME, null, cv);
        } else {
            throw new IllegalArgumentException("unsupported parameter");
        }
        closeDatabase();
        return id;
    }

    /**
     * Update Item in Database
     *
     * @param newObject The object need to be update.
     * @return the number of rows affected. -1 if error.
     */
    protected int update(Object newObject) {
        SQLiteDatabase db = openDatabase();
        int rows = -1;
        if (newObject instanceof FileItem) {
            ContentValues cv = new ContentValues();
            cv.put(SyncFile.Columns.FILENAME, ((FileItem) newObject).getFilename());
            cv.put(SyncFile.Columns.PATH, ((FileItem) newObject).getPath());
            cv.put(SyncFile.Columns.IS_FOLDER, ((FileItem) newObject).isFolder());
            cv.put(SyncFile.Columns.PARENT_ID, ((FileItem) newObject).getParentId());
            cv.put(SyncFile.Columns.CREATE_TIME, ((FileItem) newObject).getCreateTime());
            cv.put(SyncFile.Columns.HASH_VALUE, ((FileItem) newObject).getHashValue());
            String[] args = {String.valueOf(((FileItem) newObject).getId())};
            rows = db.update(SyncFile.TABLE_NAME, cv, SyncFile.Columns.ID + "=?", args);
        } else if (newObject instanceof ActionItem) {
            ContentValues cv = new ContentValues();
            cv.put(SyncAction.Columns.TYPE, ((ActionItem) newObject).getType());
            cv.put(SyncAction.Columns.REMOTE_PATH, ((ActionItem) newObject).getRemotePath());
            cv.put(SyncAction.Columns.LOCAL_PATH, ((ActionItem) newObject).getLocalPath());
            cv.put(SyncAction.Columns.CREATE_TIME, ((ActionItem) newObject).getCreateTime());
            cv.put(SyncAction.Columns.STATE, ((ActionItem) newObject).getState());
            cv.put(SyncAction.Columns.MISC, ((ActionItem) newObject).getMisc());
            String[] args = {String.valueOf(((ActionItem) newObject).getId())};
            rows = db.update(SyncAction.TABLE_NAME, cv, SyncAction.Columns.ID + "=?", args);
        } else {
            throw new IllegalArgumentException("unsupported parameter");
        }
        closeDatabase();
        return rows;
    }

    /**
     * Delete Item in Databese
     *
     * @param object The object will be deleted.
     * @return the number of rows affected. -1 if error.
     */
    protected int delete(Object object) {
        int rows = -1;
        SQLiteDatabase db = openDatabase();
        db.beginTransaction();
        if (object instanceof FileItem) {
            String[] args = {String.valueOf(((FileItem) object).getId())};
            rows = db.delete(SyncFile.TABLE_NAME, SyncFile.Columns.ID + "=?", args);
        } else if (object instanceof ActionItem) {
            String[] args = {String.valueOf(((ActionItem) object).getId())};
            rows = db.delete(SyncAction.TABLE_NAME, SyncAction.Columns.ID + "=?", args);
        } else {
            throw new IllegalArgumentException("unsupported parameter.");
        }
        db.setTransactionSuccessful();
        db.endTransaction();
        closeDatabase();
        return rows;
    }

    /**
     * Get all unprocessed ActionItems
     *
     * @return
     */
    protected LinkedList<ActionItem> getUnprocessedActionItems() {
        LinkedList<ActionItem> actionItems = new LinkedList<>();

        SQLiteDatabase db = openDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + SyncAction.TABLE_NAME
                + " WHERE " + SyncAction.Columns.STATE + " = " + SyncAction.STATE_DEFAULT
                + " ORDER BY " + SyncAction.Columns.ID, null);
        if (cursor.getCount() != 0 && cursor.moveToFirst()) {
            do {
                ActionItem item = new ActionItem();
                item.setId(cursor.getLong(cursor
                        .getColumnIndex(SyncAction.Columns.ID)));
                item.setType(cursor.getInt(cursor
                        .getColumnIndex(SyncAction.Columns.TYPE)));
                item.setRemotePath(cursor.getString(cursor
                        .getColumnIndex(SyncAction.Columns.REMOTE_PATH)));
                item.setLocalPath(cursor.getString(cursor
                        .getColumnIndex(SyncAction.Columns.LOCAL_PATH)));
                item.setCreateTime(cursor.getLong(cursor
                        .getColumnIndex(SyncAction.Columns.CREATE_TIME)));
                item.setState(cursor.getInt(cursor
                        .getColumnIndex(SyncAction.Columns.STATE)));
                item.setMisc(cursor.getString(cursor
                        .getColumnIndex(SyncAction.Columns.MISC)));
                actionItems.add(item);
            } while (cursor.moveToNext());
        }
        cursor.close();
        closeDatabase();
        return actionItems;
    }

    /**
     * Get all ActionItems
     *
     * @return all ActionItems
     */
    protected LinkedList<ActionItem> getAllActionItems() {
        LinkedList<ActionItem> actionItems = new LinkedList<>();

        SQLiteDatabase db = openDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + SyncAction.TABLE_NAME
                + " ORDER BY " + SyncAction.Columns.ID, null);
        if (cursor.getCount() != 0 && cursor.moveToFirst()) {
            do {
                ActionItem item = new ActionItem();
                item.setId(cursor.getLong(cursor
                        .getColumnIndex(SyncAction.Columns.ID)));
                item.setType(cursor.getInt(cursor
                        .getColumnIndex(SyncAction.Columns.TYPE)));
                item.setRemotePath(cursor.getString(cursor
                        .getColumnIndex(SyncAction.Columns.REMOTE_PATH)));
                item.setLocalPath(cursor.getString(cursor
                        .getColumnIndex(SyncAction.Columns.LOCAL_PATH)));
                item.setCreateTime(cursor.getLong(cursor
                        .getColumnIndex(SyncAction.Columns.CREATE_TIME)));
                item.setState(cursor.getInt(cursor
                        .getColumnIndex(SyncAction.Columns.STATE)));
                item.setMisc(cursor.getString(cursor
                        .getColumnIndex(SyncAction.Columns.MISC)));
                actionItems.add(item);
            } while (cursor.moveToNext());
        }
        cursor.close();
        closeDatabase();
        return actionItems;
    }

    /**
     * Get all FileItems by Parent ID
     *
     * @param id
     * @return
     */
    protected ArrayList<FileItem> getFileItemsByParentId(long id) {
        ArrayList<FileItem> fileItems = new ArrayList<FileItem>();

        SQLiteDatabase db = openDatabase();
        String[] args = new String[]{String.valueOf(id)};
        Cursor cursor = db.rawQuery("SELECT * FROM " + SyncFile.TABLE_NAME
                + " WHERE " + SyncFile.Columns.ID + " = ?", args);
        if (cursor.getCount() != 0 && cursor.moveToFirst()) {
            do {
                FileItem item = new FileItem();
                item.setId(cursor.getLong(cursor
                        .getColumnIndex(SyncFile.Columns.ID)));
                item.setFilename(cursor.getString(cursor
                        .getColumnIndex(SyncFile.Columns.FILENAME)));
                item.setPath(cursor.getString(cursor
                        .getColumnIndex(SyncFile.Columns.PATH)));
                item.setIsFolder(cursor.getInt(cursor
                        .getColumnIndex(SyncFile.Columns.IS_FOLDER)) == 1);
                item.setParentId(cursor.getLong(cursor
                        .getColumnIndex(SyncFile.Columns.PARENT_ID)));
                item.setCreateTime(cursor.getLong(cursor
                        .getColumnIndex(SyncFile.Columns.CREATE_TIME)));
                item.setHashValue(cursor.getString(cursor
                        .getColumnIndex(SyncFile.Columns.HASH_VALUE)));

                fileItems.add(item);
            } while (cursor.moveToNext());
        }
        cursor.close();
        closeDatabase();
        return fileItems;
    }

    /**
     * Get all FileItems by Parent Path
     *
     * @param path
     * @return
     */
    protected ArrayList<FileItem> getFileItemsByParentPath(String path) {
        FileItem fileItem = this.getFileItemByPath(path);
        if(fileItem == null){
            return this.getFileItemsByParentId(SyncFile.BASE_FOLDER_ID);
        }
        return this.getFileItemsByParentId(fileItem.getId());
    }

    /**
     * Get FileItem by id
     *
     * @param id
     * @return the FileItem to be found
     */
    protected FileItem getFileItemById(int id) {
        String sql = "SELECT * FROM " + SyncFile.TABLE_NAME + " WHERE " + SyncFile.Columns.ID + " = ?";
        String[] args = new String[]{String.valueOf(id)};

        return getFileItemByRawQuery(sql, args);
    }

    /**
     * Get FileItem by path
     *
     * @param path
     * @return the FileItem to be found
     */
    protected FileItem getFileItemByPath(String path) {
        String sql = "SELECT * FROM " + SyncFile.TABLE_NAME + " WHERE " + SyncFile.Columns.PATH + " = ?";
        String[] args = new String[]{path};

        return getFileItemByRawQuery(sql, args);
    }

    /**
     * Get FileItem by RawQuery
     *
     * @param sql
     * @param args
     * @return the FileItem to be found
     */
    protected FileItem getFileItemByRawQuery(String sql, String[] args) {
        SQLiteDatabase db = openDatabase();
        FileItem fileItem = null;
        Cursor cursor = db.rawQuery(sql, args);
        if (cursor.getCount() != 0 && cursor.moveToFirst()) {
            fileItem = new FileItem();
            fileItem.setId(cursor.getLong(cursor
                    .getColumnIndex(SyncFile.Columns.ID)));
            fileItem.setFilename(cursor.getString(cursor
                    .getColumnIndex(SyncFile.Columns.FILENAME)));
            fileItem.setPath(cursor.getString(cursor
                    .getColumnIndex(SyncFile.Columns.PATH)));
            fileItem.setIsFolder(cursor.getInt(cursor
                    .getColumnIndex(SyncFile.Columns.IS_FOLDER)) == 1);
            fileItem.setParentId(cursor.getLong(cursor
                    .getColumnIndex(SyncFile.Columns.PARENT_ID)));
            fileItem.setCreateTime(cursor.getLong(cursor
                    .getColumnIndex(SyncFile.Columns.CREATE_TIME)));
            fileItem.setHashValue(cursor.getString(cursor
                    .getColumnIndex(SyncFile.Columns.HASH_VALUE)));
        }
        cursor.close();
        closeDatabase();
        return fileItem;
    }

    private synchronized SQLiteDatabase openDatabase() {
        countOpenTimes = countOpenTimes + 1;
        if (countOpenTimes == 1) {
            mDatabase = mInstance.getWritableDatabase();
        }
        return mDatabase;
    }

    private synchronized void closeDatabase() {
        countOpenTimes = countOpenTimes - 1;
        if (countOpenTimes == 0) {
            mDatabase.close();
        }
    }
}
