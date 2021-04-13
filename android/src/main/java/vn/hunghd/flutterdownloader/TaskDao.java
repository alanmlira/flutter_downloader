package vn.hunghd.flutterdownloader;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

import java.util.ArrayList;
import java.util.List;

public class TaskDao {
    private TaskDbHelper dbHelper;

    final private String[] projection = new String[]{
            BaseColumns._ID,
            TaskContract.TaskEntry.COLUMN_NAME_TASK_ID,
            TaskContract.TaskEntry.COLUMN_NAME_PROGRESS,
            TaskContract.TaskEntry.COLUMN_NAME_STATUS,
            TaskContract.TaskEntry.COLUMN_NAME_URL,
            TaskContract.TaskEntry.COLUMN_NAME_FILE_NAME,
            TaskContract.TaskEntry.COLUMN_NAME_SAVED_DIR,
            TaskContract.TaskEntry.COLUMN_NAME_HEADERS,
            TaskContract.TaskEntry.COLUMN_NAME_MIME_TYPE,
            TaskContract.TaskEntry.COLUMN_NAME_RESUMABLE,
            TaskContract.TaskEntry.COLUMN_NAME_OPEN_FILE_FROM_NOTIFICATION,
            TaskContract.TaskEntry.COLUMN_NAME_SHOW_NOTIFICATION,
            TaskContract.TaskEntry.COLUMN_NAME_TIME_CREATED,
            TaskContract.TaskEntry.COLUMN_NAME_ALBUM_NAME,
            TaskContract.TaskEntry.COLUMN_NAME_ARTIST_NAME,
            TaskContract.TaskEntry.COLUMN_NAME_ARTIST_ID,
            TaskContract.TaskEntry.COLUMN_NAME_PLAYLIST_ID,
            TaskContract.TaskEntry.COLUMN_NAME_ALBUM_ID,
            TaskContract.TaskEntry.COLUMN_NAME_MUSIC_ID,
    };

    public TaskDao(TaskDbHelper helper) {
        dbHelper = helper;
    }

    public void insertOrUpdateNewTask(String taskId, String url, int status, int progress, String fileName,
                                       String savedDir, String headers, boolean showNotification, boolean openFileFromNotification,
                                      String albumName, String artistName, String artistId, String playlistId, String albumId, String musicId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(TaskContract.TaskEntry.COLUMN_NAME_TASK_ID, taskId);
        values.put(TaskContract.TaskEntry.COLUMN_NAME_URL, url);
        values.put(TaskContract.TaskEntry.COLUMN_NAME_STATUS, status);
        values.put(TaskContract.TaskEntry.COLUMN_NAME_PROGRESS, progress);
        values.put(TaskContract.TaskEntry.COLUMN_NAME_FILE_NAME, fileName);
        values.put(TaskContract.TaskEntry.COLUMN_NAME_SAVED_DIR, savedDir);
        values.put(TaskContract.TaskEntry.COLUMN_NAME_HEADERS, headers);
        values.put(TaskContract.TaskEntry.COLUMN_NAME_MIME_TYPE, "unknown");
        values.put(TaskContract.TaskEntry.COLUMN_NAME_SHOW_NOTIFICATION, showNotification ? 1 : 0);
        values.put(TaskContract.TaskEntry.COLUMN_NAME_OPEN_FILE_FROM_NOTIFICATION, openFileFromNotification ? 1 : 0);
        values.put(TaskContract.TaskEntry.COLUMN_NAME_RESUMABLE, 0);
        values.put(TaskContract.TaskEntry.COLUMN_NAME_TIME_CREATED, System.currentTimeMillis());
        values.put(TaskContract.TaskEntry.COLUMN_NAME_ALBUM_NAME, albumName);
        values.put(TaskContract.TaskEntry.COLUMN_NAME_ARTIST_NAME, artistName);
        values.put(TaskContract.TaskEntry.COLUMN_NAME_ARTIST_ID, artistId);
        values.put(TaskContract.TaskEntry.COLUMN_NAME_PLAYLIST_ID, playlistId);
        values.put(TaskContract.TaskEntry.COLUMN_NAME_ALBUM_ID, albumId);
        values.put(TaskContract.TaskEntry.COLUMN_NAME_MUSIC_ID, musicId);

        db.beginTransaction();
        try {
            db.insertWithOnConflict(TaskContract.TaskEntry.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }
    }

    public List<DownloadTask> loadAllTasks() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.query(
                TaskContract.TaskEntry.TABLE_NAME,
                projection,
                null,
                null,
                null,
                null,
                null
        );

        List<DownloadTask> result = new ArrayList<>();
        while (cursor.moveToNext()) {
            result.add(parseCursor(cursor));
        }
        cursor.close();

        return result;
    }

    public List<DownloadTask> loadTasksWithRawQuery(String query) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery(query, null);

        List<DownloadTask> result = new ArrayList<>();
        while (cursor.moveToNext()) {
            result.add(parseCursor(cursor));
        }
        cursor.close();

        return result;
    }

    public DownloadTask loadTask(String taskId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String whereClause = TaskContract.TaskEntry.COLUMN_NAME_TASK_ID + " = ?";
        String[] whereArgs = new String[]{taskId};

        Cursor cursor = db.query(
                TaskContract.TaskEntry.TABLE_NAME,
                projection,
                whereClause,
                whereArgs,
                null,
                null,
                BaseColumns._ID + " DESC",
                "1"
        );

        DownloadTask result = null;
        while (cursor.moveToNext()) {
            result = parseCursor(cursor);
        }
        cursor.close();
        return result;
    }

    public void updateTask(String taskId, int status, int progress) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(TaskContract.TaskEntry.COLUMN_NAME_STATUS, status);
        values.put(TaskContract.TaskEntry.COLUMN_NAME_PROGRESS, progress);

        db.beginTransaction();
        try {
            db.update(TaskContract.TaskEntry.TABLE_NAME, values, TaskContract.TaskEntry.COLUMN_NAME_TASK_ID + " = ?", new String[]{taskId});
            db.setTransactionSuccessful();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }
    }

    public void updateTask(String currentTaskId, String newTaskId, int status, int progress, boolean resumable) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(TaskContract.TaskEntry.COLUMN_NAME_TASK_ID, newTaskId);
        values.put(TaskContract.TaskEntry.COLUMN_NAME_STATUS, status);
        values.put(TaskContract.TaskEntry.COLUMN_NAME_PROGRESS, progress);
        values.put(TaskContract.TaskEntry.COLUMN_NAME_RESUMABLE, resumable ? 1 : 0);
        values.put(TaskContract.TaskEntry.COLUMN_NAME_TIME_CREATED, System.currentTimeMillis());

        db.beginTransaction();
        try {
            db.update(TaskContract.TaskEntry.TABLE_NAME, values, TaskContract.TaskEntry.COLUMN_NAME_TASK_ID + " = ?", new String[]{currentTaskId});
            db.setTransactionSuccessful();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }
    }

    public void updateTask(String taskId, boolean resumable) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(TaskContract.TaskEntry.COLUMN_NAME_RESUMABLE, resumable ? 1 : 0);

        db.beginTransaction();
        try {
            db.update(TaskContract.TaskEntry.TABLE_NAME, values, TaskContract.TaskEntry.COLUMN_NAME_TASK_ID + " = ?", new String[]{taskId});
            db.setTransactionSuccessful();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }
    }

    public void updateTask(String taskId, String filename, String mimeType) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(TaskContract.TaskEntry.COLUMN_NAME_FILE_NAME, filename);
        values.put(TaskContract.TaskEntry.COLUMN_NAME_MIME_TYPE, mimeType);

        db.beginTransaction();
        try {
            db.update(TaskContract.TaskEntry.TABLE_NAME, values, TaskContract.TaskEntry.COLUMN_NAME_TASK_ID + " = ?", new String[]{taskId});
            db.setTransactionSuccessful();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }
    }

    public void deleteTask(String taskId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        db.beginTransaction();
        try {
            String whereClause = TaskContract.TaskEntry.COLUMN_NAME_TASK_ID + " = ?";
            String[] whereArgs = new String[]{taskId};
            db.delete(TaskContract.TaskEntry.TABLE_NAME, whereClause, whereArgs);
            db.setTransactionSuccessful();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }
    }

    private DownloadTask parseCursor(Cursor cursor) {
        int primaryId = cursor.getInt(cursor.getColumnIndexOrThrow(BaseColumns._ID));
        String taskId = cursor.getString(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_NAME_TASK_ID));
        int status = cursor.getInt(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_NAME_STATUS));
        int progress = cursor.getInt(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_NAME_PROGRESS));
        String url = cursor.getString(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_NAME_URL));
        String filename = cursor.getString(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_NAME_FILE_NAME));
        String savedDir = cursor.getString(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_NAME_SAVED_DIR));
        String headers = cursor.getString(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_NAME_HEADERS));
        String mimeType = cursor.getString(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_NAME_MIME_TYPE));
        int resumable = cursor.getShort(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_NAME_RESUMABLE));
        int showNotification = cursor.getShort(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_NAME_SHOW_NOTIFICATION));
        int clickToOpenDownloadedFile = cursor.getShort(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_NAME_OPEN_FILE_FROM_NOTIFICATION));
        long timeCreated = cursor.getLong(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_NAME_TIME_CREATED));
        String albumName = cursor.getString(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_NAME_ALBUM_NAME));
        String artistName = cursor.getString(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_NAME_ARTIST_NAME));
        String artistId = cursor.getString(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_NAME_ARTIST_ID));
        String playlistId = cursor.getString(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_NAME_PLAYLIST_ID));
        String albumId = cursor.getString(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_NAME_ALBUM_ID));
        String musicId = cursor.getString(cursor.getColumnIndexOrThrow(TaskContract.TaskEntry.COLUMN_NAME_MUSIC_ID));
        return new DownloadTask(primaryId, taskId, status, progress, url, filename, savedDir, headers,
                mimeType, resumable == 1, showNotification == 1, clickToOpenDownloadedFile == 1, timeCreated, albumName, artistName, artistId, playlistId, albumId, musicId);
    }

}
