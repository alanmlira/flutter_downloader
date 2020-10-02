package vn.hunghd.flutterdownloader;

public class DownloadTask {
    int primaryId;
    String taskId;
    int status;
    int progress;
    String url;
    String filename;
    String savedDir;
    String headers;
    String mimeType;
    boolean resumable;
    boolean showNotification;
    boolean openFileFromNotification;
    long timeCreated;
    String albumName;
    String artistName;
    String smExtras;

    DownloadTask(int primaryId, String taskId, int status, int progress, String url, String filename, String savedDir,
                 String headers, String mimeType, boolean resumable, boolean showNotification, boolean openFileFromNotification,
                 long timeCreated, String albumName, String artistName, String smExtras) {
        this.primaryId = primaryId;
        this.taskId = taskId;
        this.status = status;
        this.progress = progress;
        this.url = url;
        this.filename = filename;
        this.savedDir = savedDir;
        this.headers = headers;
        this.mimeType = mimeType;
        this.resumable = resumable;
        this.showNotification = showNotification;
        this.openFileFromNotification = openFileFromNotification;
        this.timeCreated = timeCreated;
        this.albumName = albumName;
        this.artistName = artistName;
        this.smExtras = smExtras;
    }

    @Override
    public String toString() {
        return "DownloadTask{taskId=" + taskId + ",status=" + status + ",progress=" + progress + ",url=" + url + ",filename=" + filename + ",savedDir=" + savedDir + ",headers=" + headers + ",albumName=" + albumName + ",artistName=" + artistName + ",smExtras=" + smExtras + "}";
    }
}
