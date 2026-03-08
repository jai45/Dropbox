import { useState, useEffect } from "react";
import styles from "./FileUploadModal.module.css";
import { useFileFingerprint, fileKey } from "../hooks/useFileFingerprint";

const FileUploadModal = ({ isOpen, onClose, onSubmit, files }) => {
  const [fileNames, setFileNames] = useState([]);
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState({});

  const { fingerprintMap, computeFingerprints, isHashing, resetFingerprints } =
    useFileFingerprint();

  useEffect(() => {
    const timeoutId = setTimeout(() => {
      if (isOpen && files.length > 0) {
        const names = files.map((file) => {
          const lastDot = file.name.lastIndexOf(".");
          if (lastDot === -1) {
            return {
              name: file.name,
              extension: "",
              fullName: file.name,
              originalFile: file,
            };
          }
          const name = file.name.substring(0, lastDot);
          const extension = file.name.substring(lastDot);
          return { name, extension, fullName: file.name, originalFile: file };
        });
        setFileNames(names);
        computeFingerprints(files);
      } else {
        setFileNames([]);
        resetFingerprints();
      }
    }, 0);

    return () => clearTimeout(timeoutId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, files]);

  const handleNameChange = (index, newName) => {
    const updated = [...fileNames];
    updated[index] = { ...updated[index], name: newName };
    setFileNames(updated);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setUploading(true);

    const filesWithNewNames = fileNames.map((item) => ({
      originalFile: item.originalFile,
      newName: item.name + item.extension,
      sha256: fingerprintMap[fileKey(item.originalFile)]?.hash ?? null,
    }));

    try {
      await onSubmit(filesWithNewNames, (fileIndex, progress) => {
        setUploadProgress((prev) => ({ ...prev, [fileIndex]: progress }));
      });
      onClose();
      setUploadProgress({});
      resetFingerprints();
    } catch (error) {
      console.error("Upload failed:", error);
      alert("Upload failed: " + error.message);
    } finally {
      setUploading(false);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === "Escape") {
      onClose();
    }
  };

  if (!isOpen) return null;

  const allNamesValid = fileNames.every((item) => item.name.trim().length > 0);
  const allFingerprintsDone =
    !isHashing &&
    fileNames.every((item) => {
      const entry = fingerprintMap[fileKey(item.originalFile)];
      return entry?.status === "done" || entry?.status === "error";
    });

  // Overall hashing progress across all files (0-100)
  const overallHashProgress =
    fileNames.length === 0
      ? 0
      : Math.round(
          fileNames.reduce((sum, item) => {
            const entry = fingerprintMap[fileKey(item.originalFile)];
            return sum + (entry?.progress ?? 0);
          }, 0) / fileNames.length,
        );

  return (
    <div className={styles.overlay} onClick={onClose}>
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <div className={styles.header}>
          <h3>Upload Files</h3>
          <button onClick={onClose} className={styles.closeBtn}>
            ×
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className={styles.fileList}>
            {fileNames.map((item, index) => (
              <div key={index} className={styles.fileItem}>
                <span className={styles.fileIcon}>📄</span>
                <div className={styles.nameInput}>
                  <input
                    type="text"
                    value={item.name}
                    onChange={(e) => handleNameChange(index, e.target.value)}
                    onKeyDown={handleKeyDown}
                    className={styles.input}
                    placeholder="File name"
                    autoFocus={index === 0}
                    disabled={uploading}
                  />
                  <span className={styles.extension}>{item.extension}</span>
                </div>
                <span className={styles.fileSize}>
                  {(item.originalFile.size / 1024).toFixed(1)} KB
                </span>
                {uploading && uploadProgress[index] !== undefined && (
                  <span className={styles.progress}>
                    {uploadProgress[index]}%
                  </span>
                )}
              </div>
            ))}
          </div>

          <div className={styles.actions}>
            <button
              type="button"
              onClick={onClose}
              className={styles.cancelBtn}
              disabled={uploading}
            >
              Cancel
            </button>
            <button
              type="submit"
              className={styles.submitBtn}
              disabled={!allNamesValid || uploading || !allFingerprintsDone}
            >
              {uploading
                ? "Uploading…"
                : isHashing
                  ? `Preparing upload ${overallHashProgress}%`
                  : `Upload ${fileNames.length} ${fileNames.length === 1 ? "File" : "Files"}`}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default FileUploadModal;
