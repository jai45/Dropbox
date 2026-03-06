import styles from "./FileList.module.css";

const FileList = ({
  files,
  folders,
  onFileDelete,
  onFolderDelete,
  onFileDownload,
  onFolderClick,
}) => {
  const formatFileSize = (bytes) => {
    if (bytes === 0) return "0 Bytes";
    const k = 1024;
    const sizes = ["Bytes", "KB", "MB", "GB"];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + " " + sizes[i];
  };

  const formatDate = (dateString) => {
    const date = new Date(dateString);
    return (
      date.toLocaleDateString() +
      " " +
      date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
    );
  };

  const getFileIcon = (type) => {
    if (type.startsWith("image/")) return "🖼️";
    if (type.startsWith("video/")) return "🎥";
    if (type.startsWith("audio/")) return "🎵";
    if (type.includes("pdf")) return "📄";
    if (type.includes("text")) return "📝";
    if (type.includes("zip") || type.includes("compressed")) return "🗜️";
    return "📄";
  };

  return (
    <div className={styles.container}>
      <div className={styles.grid}>
        {folders.map((folder) => (
          <div key={folder.id} className={styles.item}>
            <div
              className={styles.itemHeader}
              onClick={() => onFolderClick(folder.id)}
            >
              <span className={styles.icon}>📁</span>
              <span className={styles.name}>{folder.name}</span>
            </div>
            <div className={styles.itemFooter}>
              <span className={styles.meta}>
                {formatDate(folder.createdAt)}
              </span>
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  if (window.confirm(`Delete folder "${folder.name}"?`)) {
                    onFolderDelete(folder.id);
                  }
                }}
                className={styles.deleteBtn}
              >
                🗑️
              </button>
            </div>
          </div>
        ))}

        {files.map((file) => (
          <div key={file.id} className={styles.item}>
            <div className={styles.itemHeader}>
              <span className={styles.icon}>{getFileIcon(file.type)}</span>
              <span className={styles.name}>{file.name}</span>
            </div>
            <div className={styles.itemFooter}>
              <span className={styles.meta}>
                {formatFileSize(file.size)} • {formatDate(file.uploadedAt)}
              </span>
              <div className={styles.actions}>
                <button
                  onClick={() => onFileDownload(file)}
                  className={styles.downloadBtn}
                  title="Download"
                >
                  ⬇️
                </button>
                <button
                  onClick={() => {onFileDelete(file.id);}}
                  className={styles.deleteBtn}
                  title="Delete"
                >
                  🗑️
                </button>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default FileList;
