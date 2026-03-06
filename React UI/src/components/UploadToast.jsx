import styles from "./UploadToast.module.css";

const statusIcon = (upload) => {
  if (upload.status === "error") return "❌";
  if (upload.status === "cancelled") return "🚫";
  if (upload.status === "done") return "✅";
  return "📄";
};

const UploadToast = ({ uploads, onDismiss, onCancel }) => {
  if (uploads.length === 0) return null;

  const allDone = uploads.every(
    (u) =>
      u.status === "done" || u.status === "error" || u.status === "cancelled",
  );
  const totalProgress = Math.round(
    uploads.reduce((sum, u) => sum + u.progress, 0) / uploads.length,
  );

  return (
    <div className={styles.toast}>
      <div className={styles.header}>
        <span className={styles.title}>
          {allDone ? (
            <>✅ All done</>
          ) : (
            <>
              <span className={styles.rocket}>🚀</span> Sending to the cloud…{" "}
              {totalProgress}%
            </>
          )}
        </span>
        {allDone && (
          <button className={styles.closeBtn} onClick={onDismiss}>
            ✕
          </button>
        )}
      </div>

      <div className={styles.list}>
        {uploads.map((upload) => (
          <div key={upload.id} className={styles.item}>
            <div className={styles.nameRow}>
              <span className={styles.fileName} title={upload.name}>
                {statusIcon(upload)}{" "}
                {upload.name.length > 24
                  ? upload.name.slice(0, 21) + "…"
                  : upload.name}
              </span>
              <div className={styles.right}>
                <span className={styles.percent}>
                  {upload.status === "error"
                    ? "Failed"
                    : upload.status === "cancelled"
                      ? "Cancelled"
                      : `${upload.progress}%`}
                </span>
                {upload.status === "uploading" && (
                  <button
                    className={styles.cancelBtn}
                    onClick={() => onCancel(upload.id)}
                  >
                    Cancel
                  </button>
                )}
              </div>
            </div>
            <div className={styles.barBg}>
              <div
                className={`${styles.bar} ${
                  upload.status === "error"
                    ? styles.barError
                    : upload.status === "cancelled"
                      ? styles.barCancelled
                      : upload.status === "done"
                        ? styles.barDone
                        : styles.barActive
                }`}
                style={{ width: `${upload.progress}%` }}
              />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default UploadToast;
