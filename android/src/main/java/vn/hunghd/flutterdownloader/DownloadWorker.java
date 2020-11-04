package vn.hunghd.flutterdownloader;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.net.Uri;

import java.io.File;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.mpatric.mp3agic.ID3v1Tag;
import com.mpatric.mp3agic.ID3v24Tag;
import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.Mp3File;
import com.mpatric.mp3agic.NotSupportedException;
import com.mpatric.mp3agic.UnsupportedTagException;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.PluginRegistrantCallback;
import io.flutter.view.FlutterCallbackInformation;
import io.flutter.view.FlutterMain;
import io.flutter.view.FlutterNativeView;
import io.flutter.view.FlutterRunArguments;

public class DownloadWorker extends Worker implements MethodChannel.MethodCallHandler {
    public static final String ARG_URL = "url";
    public static final String ARG_FILE_NAME = "file_name";
    public static final String ARG_SAVED_DIR = "saved_file";
    public static final String ARG_HEADERS = "headers";
    public static final String ARG_IS_RESUME = "is_resume";
    public static final String ARG_SHOW_NOTIFICATION = "show_notification";
    public static final String ARG_OPEN_FILE_FROM_NOTIFICATION = "open_file_from_notification";
    public static final String ARG_CALLBACK_HANDLE = "callback_handle";
    public static final String ARG_DEBUG = "debug";
    public static final String ARG_MUSIC_ARTIST = "music_artist";
    public static final String ARG_MUSIC_ALBUM = "music_album";
    public static final String ARG_SM_EXTRAS = "sm_extras";
    public static final String ARG_ARTIST_ID = "artist_id";
    public static final String ARG_PLAYLIST_ID = "playlist_id";
    public static final String ARG_ALBUM_ID = "album_id";
    public static final String ARG_MUSIC_ID = "music_id";

    public static final String IS_PENDING = "is_pending";
    public static final String USER_AGENT = "SuaMusica/downloader (Linux; Android "+Build.VERSION.SDK_INT+"; "+Build.BRAND+"/"+Build.MODEL+")";

    private static final String TAG = DownloadWorker.class.getSimpleName();
    private static final int BUFFER_SIZE = 4096;
    private static final String CHANNEL_ID = "FLUTTER_DOWNLOADER_NOTIFICATION";
    private static final int STEP_UPDATE = 10;

    private static final AtomicBoolean isolateStarted = new AtomicBoolean(false);
    private static final ArrayDeque<List> isolateQueue = new ArrayDeque<>();
    private static FlutterNativeView backgroundFlutterView;

    private final Pattern charsetPattern = Pattern.compile("(?i)\\bcharset=\\s*\"?([^\\s;\"]*)");

    private MethodChannel backgroundChannel;
    private TaskDbHelper dbHelper;
    private TaskDao taskDao;
    private NotificationCompat.Builder builder;
    private boolean showNotification;
    private boolean clickToOpenDownloadedFile;
    private boolean debug;
    private int lastProgress = 0;
    private int primaryId;
    private String msgStarted;
    private String  msgInProgress;
    private String msgCanceled;
    private String msgFailed;
    private String msgPaused;
    private String msgComplete;
    private String argMusicArtist;
    private String argMusicAlbum;
    private String argArtistId;
    private String argPlaylistId;
    private String argAlbumId;
    private String argMusicId;

    public DownloadWorker(@NonNull final Context context, @NonNull WorkerParameters params) {
        super(context, params);

        new Handler(context.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                startBackgroundIsolate(context);
            }
        });
    }

    private void startBackgroundIsolate(Context context) {
        synchronized (isolateStarted) {
            if (backgroundFlutterView == null) {
                SharedPreferences pref = context.getSharedPreferences(
                        FlutterDownloaderPlugin.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
                long callbackHandle =
                        pref.getLong(FlutterDownloaderPlugin.CALLBACK_DISPATCHER_HANDLE_KEY, 0);

                FlutterMain.startInitialization(context); // Starts initialization of the native
                // system, if already initialized this
                // does nothing
                FlutterMain.ensureInitializationComplete(context, null);

                FlutterCallbackInformation callbackInfo =
                        FlutterCallbackInformation.lookupCallbackInformation(callbackHandle);
                if (callbackInfo == null) {
                    Log.e(TAG, "Fatal: failed to find callback");
                    return;
                }

                backgroundFlutterView = new FlutterNativeView(getApplicationContext(), true);

                /// backward compatibility with V1 embedding
                if (getApplicationContext() instanceof PluginRegistrantCallback) {
                    PluginRegistrantCallback pluginRegistrantCallback =
                            (PluginRegistrantCallback) getApplicationContext();
                    PluginRegistry registry = backgroundFlutterView.getPluginRegistry();
                    pluginRegistrantCallback.registerWith(registry);
                }

                FlutterRunArguments args = new FlutterRunArguments();
                args.bundlePath = FlutterMain.findAppBundlePath();
                args.entrypoint = callbackInfo.callbackName;
                args.libraryPath = callbackInfo.callbackLibraryPath;

                backgroundFlutterView.runFromBundle(args);
            }
        }

        backgroundChannel =
                new MethodChannel(backgroundFlutterView, "vn.hunghd/downloader_background");
        backgroundChannel.setMethodCallHandler(this);
    }

    @Override
    public void onMethodCall(MethodCall call, MethodChannel.Result result) {
        if (call.method.equals("didInitializeDispatcher")) {
            synchronized (isolateStarted) {
                while (!isolateQueue.isEmpty()) {
                    backgroundChannel.invokeMethod("", isolateQueue.remove());
                }
                isolateStarted.set(true);
                result.success(null);
            }
        } else {
            result.notImplemented();
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        dbHelper = TaskDbHelper.getInstance(context);
        taskDao = new TaskDao(dbHelper);

        String url = getInputData().getString(ARG_URL);
        String filename = getInputData().getString(ARG_FILE_NAME);
        String savedDir = getInputData().getString(ARG_SAVED_DIR);
        String headers = getInputData().getString(ARG_HEADERS);
        boolean isResume = getInputData().getBoolean(ARG_IS_RESUME, false);
        debug = getInputData().getBoolean(ARG_DEBUG, false);
        argMusicArtist = getInputData().getString(ARG_MUSIC_ARTIST);
        argMusicAlbum = getInputData().getString(ARG_MUSIC_ALBUM);
        argArtistId = getInputData().getString(ARG_ARTIST_ID);
        argPlaylistId = getInputData().getString(ARG_PLAYLIST_ID);
        argAlbumId = getInputData().getString(ARG_ALBUM_ID);
        argMusicId = getInputData().getString(ARG_MUSIC_ID);

        Resources res = getApplicationContext().getResources();
        msgStarted = res.getString(R.string.flutter_downloader_notification_started);
        msgInProgress = res.getString(R.string.flutter_downloader_notification_in_progress);
        msgCanceled = res.getString(R.string.flutter_downloader_notification_canceled);
        msgFailed = res.getString(R.string.flutter_downloader_notification_failed);
        msgPaused = res.getString(R.string.flutter_downloader_notification_paused);
        msgComplete = res.getString(R.string.flutter_downloader_notification_complete);

        Log.i(TAG, "DownloadWorker{url=" + url + ",filename=" + filename + ",savedDir=" + savedDir
                + ",header=" + headers + ",isResume=" + isResume + ",argMusicArtist=" + argMusicArtist
                + ",argMusicAlbum=" + argMusicAlbum + ",argArtistId=" + argArtistId +
                ",argArtistId=" + argArtistId + ",argPlaylistId=" + argPlaylistId +
                ",argAlbumId=" + argAlbumId + ",argMusicId=" + argMusicId);

        showNotification = getInputData().getBoolean(ARG_SHOW_NOTIFICATION, false);
        clickToOpenDownloadedFile =
                getInputData().getBoolean(ARG_OPEN_FILE_FROM_NOTIFICATION, false);

        DownloadTask task = taskDao.loadTask(getId().toString());
        primaryId = task.primaryId;

        buildNotification(context);

        updateNotification(context, filename == null ? url : filename, DownloadStatus.RUNNING,
                task.progress,"", null);
        taskDao.updateTask(getId().toString(), DownloadStatus.RUNNING, 0);

        try {
            downloadFile(context, url, savedDir, filename, headers, isResume);
            cleanUp();
            dbHelper = null;
            taskDao = null;
            return Result.success();
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            updateNotification(context, filename == null ? url : filename, DownloadStatus.FAILED,
                    -1,(errorMessage != null) ? errorMessage : "No Message",null);
            taskDao.updateTask(getId().toString(), DownloadStatus.FAILED, lastProgress);
            e.printStackTrace();
            dbHelper = null;
            taskDao = null;
            return Result.failure();
        }
    }

    private void setupHeaders(HttpURLConnection conn, String headers) {
        if (!TextUtils.isEmpty(headers)) {
            log("Headers = " + headers);
            try {
                JSONObject json = new JSONObject(headers);
                for (Iterator<String> it = json.keys(); it.hasNext(); ) {
                    String key = it.next();
                    conn.setRequestProperty(key, json.getString(key));
                }
                conn.setDoInput(true);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private long setupPartialDownloadedDataHeader(HttpURLConnection conn, String filename,
                                                  String savedDir) {
        String saveFilePath = savedDir + File.separator + filename;
        File partialFile = new File(saveFilePath);
        long downloadedBytes = partialFile.length();
        log("Resume download: Range: bytes=" + downloadedBytes + "-");
        conn.setRequestProperty("Accept-Encoding", "identity");
        conn.setRequestProperty("Range", "bytes=" + downloadedBytes + "-");
        conn.setDoInput(true);
        return downloadedBytes;
    }

    private void downloadFile(Context context, String fileURL, String savedDir, String filename,
                              String headers, boolean isResume) throws IOException {
        String url = fileURL;
        URL resourceUrl, base, next;
        Map<String, Integer> visited;
        HttpURLConnection httpConn = null;
        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        String saveFilePath;
        String location;
        long downloadedBytes = 0;
        int responseCode;
        int times;

        visited = new HashMap<>();

        try {
            // handle redirection logic
            while (true) {
                if (!visited.containsKey(url)) {
                    times = 1;
                    visited.put(url, times);
                } else {
                    times = visited.get(url) + 1;
                }

                if (times > 3)
                    throw new IOException("Stuck in redirect loop");

                resourceUrl = new URL(url);
                log("Open connection to " + url);
                httpConn = (HttpURLConnection) resourceUrl.openConnection();

                httpConn.setConnectTimeout(15000);
                httpConn.setReadTimeout(15000);
                httpConn.setInstanceFollowRedirects(false); // Make the logic below easier to detect
                // redirections
                log("Using Agent " + USER_AGENT);
                httpConn.setRequestProperty("User-Agent", USER_AGENT);

                // setup request headers if it is set
                setupHeaders(httpConn, headers);
                // try to continue downloading a file from its partial downloaded data.
                if (isResume) {
                    downloadedBytes =
                            setupPartialDownloadedDataHeader(httpConn, filename, savedDir);
                }

                responseCode = httpConn.getResponseCode();
                switch (responseCode) {
                    case HttpURLConnection.HTTP_MOVED_PERM:
                    case HttpURLConnection.HTTP_SEE_OTHER:
                    case HttpURLConnection.HTTP_MOVED_TEMP:
                        log("Response with redirection code");
                        location = httpConn.getHeaderField("Location");
                        log("Location = " + location);
                        base = new URL(fileURL);
                        next = new URL(base, location); // Deal with relative URLs
                        url = next.toExternalForm();
                        log("New url: " + url);
                        continue;
                }

                break;
            }

            httpConn.connect();

            if ((responseCode == HttpURLConnection.HTTP_OK
                    || (isResume && responseCode == HttpURLConnection.HTTP_PARTIAL))
                    && !isStopped()) {
                String contentType = httpConn.getContentType();
                int contentLength = httpConn.getContentLength();
                log("Content-Type = " + contentType);
                log("Content-Length = " + contentLength);

                String charset = getCharsetFromContentType(contentType);
                log("Charset = " + charset);
                if (!isResume) {
                    // try to extract filename from HTTP headers if it is not given by user
                    if (filename == null) {
                        String disposition = httpConn.getHeaderField("Content-Disposition");
                        log("Content-Disposition = " + disposition);
                        if (disposition != null && !disposition.isEmpty()) {
                            String name = disposition
                                    .replaceFirst("(?i)^.*filename=\"?([^\"]+)\"?.*$", "$1");
                            filename = URLDecoder.decode(name,
                                    charset != null ? charset : "ISO-8859-1");
                        }
                        if (filename == null || filename.isEmpty()) {
                            filename = url.substring(url.lastIndexOf("/") + 1);
                        }
                    }
                }
                saveFilePath = savedDir + File.separator + filename;

                log("fileName = " + filename);

                taskDao.updateTask(getId().toString(), filename, contentType);

                // opens input stream from the HTTP connection
                inputStream = httpConn.getInputStream();

                // opens an output stream to save into file
                outputStream = new FileOutputStream(saveFilePath, isResume);

                long count = downloadedBytes;
                int bytesRead = -1;
                byte[] buffer = new byte[BUFFER_SIZE];
                while ((bytesRead = inputStream.read(buffer)) != -1 && !isStopped()) {
                    count += bytesRead;
                    int progress = (int) ((count * 100) / (contentLength + downloadedBytes));
                    outputStream.write(buffer, 0, bytesRead);

                    if ((lastProgress == 0 || progress > lastProgress + STEP_UPDATE
                            || progress == 100) && progress != lastProgress) {
                        lastProgress = progress;
                        updateNotification(context, filename, DownloadStatus.RUNNING, progress,"",
                                null);

                        // This line possibly causes system overloaded because of accessing to DB
                        // too many ?!!!
                        // but commenting this line causes tasks loaded from DB missing current
                        // downloading progress,
                        // however, this missing data should be temporary and it will be updated as
                        // soon as
                        // a new bunch of data fetched and a notification sent
                        taskDao.updateTask(getId().toString(), DownloadStatus.RUNNING, progress);
                    }
                }

                DownloadTask task = taskDao.loadTask(getId().toString());
                int progress = isStopped() && task.resumable ? lastProgress : 100;
                int status = isStopped()
                        ? (task.resumable ? DownloadStatus.PAUSED : DownloadStatus.CANCELED)
                        : DownloadStatus.COMPLETE;
                int storage = ContextCompat.checkSelfPermission(getApplicationContext(),
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
                PendingIntent pendingIntent = null;
                if (status == DownloadStatus.COMPLETE) {
                    if (isMediaFile(contentType) && isExternalStoragePath(saveFilePath)) {
                        addMediaToGallery(filename, saveFilePath,
                                getContentTypeWithoutCharset(contentType));
                    }
                    if (clickToOpenDownloadedFile && storage == PackageManager.PERMISSION_GRANTED) {
                        Intent intent = IntentUtils.validatedFileIntent(getApplicationContext(),
                                saveFilePath, contentType);
                        if (intent != null) {
                            log("Setting an intent to open the file " + saveFilePath);
                            pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0,
                                    intent, PendingIntent.FLAG_CANCEL_CURRENT);
                        } else {
                            log("There's no application that can open the file " + saveFilePath);
                        }
                    }
                }
                Log.d(TAG, "===> updateNotification: (filename: " + filename + ", status: " + status
                        + ")");
                updateNotification(context, filename, status, progress, isStopped() ? "Download canceled" : "", pendingIntent);
                taskDao.updateTask(getId().toString(), status, progress);

                log(isStopped() ? "Download canceled" : "File downloaded");
            } else {
                DownloadTask task = taskDao.loadTask(getId().toString());
                int status = isStopped()
                        ? (task.resumable ? DownloadStatus.PAUSED : DownloadStatus.CANCELED)
                        : DownloadStatus.FAILED;
                String errorMessage = isStopped() ? "Download canceled"
                        : "Server replied HTTP code: " + responseCode;
                updateNotification(context, filename, status, -1, errorMessage, null);
                taskDao.updateTask(getId().toString(), status, lastProgress);
                log(errorMessage);
            }
        } catch (IOException e) {
            String errorMessage = e.getMessage();
            updateNotification(context, filename == null ? fileURL : filename,
                    DownloadStatus.FAILED, -1, (errorMessage != null) ? errorMessage : "No Message 2",null);
            taskDao.updateTask(getId().toString(), DownloadStatus.FAILED, lastProgress);
            e.printStackTrace();
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (httpConn != null) {
                httpConn.disconnect();
            }
        }
    }

    private void cleanUp() {
        DownloadTask task = taskDao.loadTask(getId().toString());
        if (task != null && task.status != DownloadStatus.COMPLETE && !task.resumable) {
            String filename = task.filename;
            if (filename == null) {
                filename = task.url.substring(task.url.lastIndexOf("/") + 1, task.url.length());
            }

            // check and delete uncompleted file
            String saveFilePath = task.savedDir + File.separator + filename;
            File tempFile = new File(saveFilePath);
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    private void buildNotification(Context context) {
        // Make a channel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel, but only on API 26+ because
            // the NotificationChannel class is new and not in the support library

            CharSequence name = context.getApplicationInfo().loadLabel(context.getPackageManager());
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setSound(null, null);

            // Add the channel
            NotificationManager notificationManager =
                    context.getSystemService(NotificationManager.class);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }

        // Create the notification
        builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                // .setSmallIcon(R.drawable.ic_download)
                .setOnlyAlertOnce(true).setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

    }

    private void updateNotification(Context context, String title, int status, int progress, String errorType,
                                    PendingIntent intent) {
        builder.setContentTitle(title);
        builder.setContentIntent(intent);
        boolean shouldUpdate = false;

        if (status == DownloadStatus.RUNNING) {
            shouldUpdate = true;
            builder.setContentText(progress == 0 ? msgStarted : msgInProgress).setProgress(100,
                    progress, progress == 0);
            builder.setOngoing(true).setSmallIcon(android.R.drawable.stat_sys_download);
        } else if (status == DownloadStatus.CANCELED) {
            shouldUpdate = true;
            builder.setContentText(msgCanceled).setProgress(0, 0, false);
            builder.setOngoing(false).setSmallIcon(android.R.drawable.stat_sys_download_done);
        } else if (status == DownloadStatus.FAILED) {
            shouldUpdate = true;
            builder.setContentText(msgFailed).setProgress(0, 0, false);
            builder.setOngoing(false).setSmallIcon(android.R.drawable.stat_sys_download_done);
        } else if (status == DownloadStatus.PAUSED) {
            shouldUpdate = true;
            builder.setContentText(msgPaused).setProgress(0, 0, false);
            builder.setOngoing(false).setSmallIcon(android.R.drawable.stat_sys_download_done);
        } else if (status == DownloadStatus.COMPLETE) {
            shouldUpdate = true;
            builder.setContentText(msgComplete).setProgress(0, 0, false);
            builder.setOngoing(false).setSmallIcon(android.R.drawable.stat_sys_download_done);
        }

        // Show the notification
        if (showNotification && shouldUpdate) {
            NotificationManagerCompat.from(context).notify(primaryId, builder.build());
        }

        sendUpdateProcessEvent(status, progress,errorType);
    }

    private void sendUpdateProcessEvent(int status, int progress,String errorType) {
        final List<Object> args = new ArrayList<>();
        long callbackHandle = getInputData().getLong(ARG_CALLBACK_HANDLE, 0);
        args.add(callbackHandle);
        args.add(getId().toString());
        args.add(status);
        args.add(progress);
        args.add(errorType);

        synchronized (isolateStarted) {
            if (!isolateStarted.get()) {
                isolateQueue.add(args);
            } else {
                new Handler(getApplicationContext().getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        backgroundChannel.invokeMethod("", args);
                    }
                });
            }
        }
    }

    private String getCharsetFromContentType(String contentType) {
        if (contentType == null)
            return null;

        Matcher m = charsetPattern.matcher(contentType);
        if (m.find()) {
            return m.group(1).trim().toUpperCase();
        }
        return null;
    }

    private String getContentTypeWithoutCharset(String contentType) {
        if (contentType == null)
            return null;
        return contentType.split(";")[0].trim();
    }

    private boolean isMediaFile(String contentType) {
        contentType = getContentTypeWithoutCharset(contentType);
        return (contentType != null && (contentType.startsWith("image/")
                || contentType.startsWith("video") || contentType.startsWith("audio")
                || contentType.contains("octet-stream")));
    }

    private boolean isExternalStoragePath(String filePath) {
        File externalStorageDir = Environment.getExternalStorageDirectory();
        return filePath != null && externalStorageDir != null
                && filePath.startsWith(externalStorageDir.getPath());
    }

    private void addMediaToGallery(String fileName, String filePath, String contentType) {
        if (contentType != null && filePath != null && fileName != null) {
            if (contentType.startsWith("image/")) {
                ContentValues values = new ContentValues();

                values.put(MediaStore.Images.Media.TITLE, fileName);
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.DESCRIPTION, "");
                values.put(MediaStore.Images.Media.MIME_TYPE, contentType);
                values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis());
                values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
                values.put(MediaStore.Images.Media.DATA, filePath);

                log("insert " + values + " to MediaStore");

                ContentResolver contentResolver = getApplicationContext().getContentResolver();
                contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            } else if (contentType.startsWith("video")) {
                ContentValues values = new ContentValues();

                values.put(MediaStore.Video.Media.TITLE, fileName);
                values.put(MediaStore.Video.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Video.Media.DESCRIPTION, "");
                values.put(MediaStore.Video.Media.MIME_TYPE, contentType);
                values.put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis());
                values.put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis());
                values.put(MediaStore.Video.Media.DATA, filePath);

                log("insert " + values + " to MediaStore");

                ContentResolver contentResolver = getApplicationContext().getContentResolver();
                contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            } else if (contentType.startsWith("audio") || contentType.contains("octet-stream")) {
                File file = new File(filePath);
                if (file.exists()) {
                    if (android.os.Build.VERSION.SDK_INT >= 29) {
                        ContentValues values = new ContentValues();
                        values.put(MediaStore.Audio.Media.TITLE, fileName);
                        values.put(MediaStore.Audio.Media.DISPLAY_NAME, fileName);
                        values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg");
                        values.put(MediaStore.Audio.Media.DATE_ADDED, System.currentTimeMillis());
                        values.put(MediaStore.Audio.Media.DATA, filePath);
                        values.put(MediaStore.Audio.Media.SIZE, file.getTotalSpace());

                        values.put(MediaStore.Audio.Media.ARTIST, argMusicArtist);
                        values.put(MediaStore.Audio.Media.ALBUM, argMusicAlbum);

                        try {
                            Mp3File mp3File = new Mp3File(filePath);
                            ID3v1Tag id3v1Tag = new ID3v1Tag();
                            id3v1Tag.setComment("Sua Música");
                            mp3File.setId3v1Tag(id3v1Tag);


                            ID3v24Tag id3v2Tag = new ID3v24Tag();
                            id3v2Tag.setAlbum(argMusicAlbum);
                            id3v2Tag.setAlbumArtist(argMusicArtist);
                            id3v2Tag.setUrl(String.format("https://www.suamusica.com.br/perfil/%s?playlistId=%s&albumId=%s&musicId=%s", argArtistId, argPlaylistId, argAlbumId, argMusicId));
                            mp3File.setId3v2Tag(id3v2Tag);

                            String newFilename = filePath + ".tmp";
                            mp3File.save(newFilename);

                            File from = new File(newFilename);
                            from.renameTo(file);

                            Log.i(TAG, "Successfully set ID3v1 tags");
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to set ID3v1 tags", e);
                        }
                        // For reasons I could not understand, Android SDK is failing to find the
                        // constant MediaStore.Audio.Media.ALBUM_ARTIST in pre-compilation time and
                        // obligated me to reference the column string value.
                        // However it's working just fine.
                        values.put("album_artist", argMusicArtist);

                        values.put(IS_PENDING, 1);
                        log("insert " + values + " to MediaStore");
                        ContentResolver contentResolver = getApplicationContext().getContentResolver();
                        Uri uriSavedMusic = contentResolver
                                .insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
                        if (uriSavedMusic != null) {
                            values.clear();
                            values.put(IS_PENDING, 0);
                            contentResolver.update(uriSavedMusic, values, null, null);
                        }
                    } else {
                        Intent scanFileIntent =
                                new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file));
                        getApplicationContext().sendBroadcast(scanFileIntent);
                    }
                }
            }
        }
    }

    private void log(String message) {
        if (debug) {
            Log.d(TAG, message);
        }
    }
}
