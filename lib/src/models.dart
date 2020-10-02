///
/// A class defines a set of possible statuses of a download task
///
class DownloadTaskStatus {
  final int _value;

  const DownloadTaskStatus(int value) : _value = value;

  int get value => _value;

  get hashCode => _value;

  operator ==(status) => status._value == this._value;

  toString() => 'DownloadTaskStatus($_value)';

  static DownloadTaskStatus from(int value) => DownloadTaskStatus(value);

  static const undefined = const DownloadTaskStatus(0);
  static const enqueued = const DownloadTaskStatus(1);
  static const running = const DownloadTaskStatus(2);
  static const complete = const DownloadTaskStatus(3);
  static const failed = const DownloadTaskStatus(4);
  static const canceled = const DownloadTaskStatus(5);
  static const paused = const DownloadTaskStatus(6);
}

///
/// A model class for incomming downloads.
///
/// * [url]: download link
/// * [savedDir]: absolute path of the directory where downloaded file is saved
/// * [fileName]: name of downloaded file. If this parameter is not set, the
/// plugin will try to extract a file name from HTTP headers response or `url`
///
class DownloadItem {
  final String url;
  final String savedDir;
  final String fileName;
  final String albumName;
  final String artistName;
  final String smExtras;

  DownloadItem({this.url, this.savedDir, this.fileName, this.albumName, this.artistName, this.smExtras});

  @override
  String toString() =>
      "DownloadItem(url: $url, savedDir: $savedDir, fileName: $fileName, albumName: $albumName, artistName: $artistName, albumName: $albumName)";

  Map<String, String> toMap() =>
      {"url": url, "saved_dir": savedDir, "file_name": fileName, "music_album": albumName, "music_artist": artistName, "sm_extras": smExtras}};
}

///
/// A model class encapsulates all task information according to data in Sqlite
/// database.
///
/// * [taskId] the unique identifier of a download task
/// * [status] the latest status of a download task
/// * [progress] the latest progress value of a download task
/// * [url] the download link
/// * [filename] the local file name of a downloaded file
/// * [savedDir] the absolute path of the directory where the downloaded file is saved
///
class DownloadTask {
  final String taskId;
  final DownloadTaskStatus status;
  final int progress;
  final String url;
  final String filename;
  final String savedDir;
  final int timeCreated;
  final String albumName;
  final String artistName;
  final String smExtras;

  DownloadTask(
      {this.taskId,
      this.status,
      this.progress,
      this.url,
      this.filename,
      this.savedDir,
      this.timeCreated,
      this.albumName,
      this.artistName,
      this.smExtras});

  @override
  String toString() =>
      "DownloadTask(taskId: $taskId, status: $status, progress: $progress, "
          "url: $url, filename: $filename, savedDir: $savedDir, timeCreated: $timeCreated, "
          "albumName: $albumName, artistName: $artistName, smExtras: $smExtras)";
}
