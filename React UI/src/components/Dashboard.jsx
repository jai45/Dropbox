import { useState, useEffect, useRef } from "react";
import Header from "./Header";
import FileList from "./FileList";
import Modal from "./Modal";
import FileUploadModal from "./FileUploadModal";
import UploadToast from "./UploadToast";
import ConfirmDialog from "./ConfirmDialog";
import { fileService } from "../services/fileService";
import { useAuth } from "../contexts/AuthContext";
import styles from "./Dashboard.module.css";

const Dashboard = () => {
  const { user } = useAuth();
  const [files, setFiles] = useState([]);
  const [folders, setFolders] = useState(() => {
    return JSON.parse(localStorage.getItem("folders") || "[]");
  });
  const [currentFolder, setCurrentFolder] = useState(null);
  const [showFolderModal, setShowFolderModal] = useState(false);
  const [showUploadModal, setShowUploadModal] = useState(false);
  const [selectedFiles, setSelectedFiles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [uploadQueue, setUploadQueue] = useState([]);
  const [confirmDelete, setConfirmDelete] = useState(null); // { type: 'file'|'folder', id, name }
  const hasFetched = useRef(false);
  const abortControllersRef = useRef({});

  // Use userId from authenticated user
  const ownerId = user?.userId;

  // Fetch files from API on mount
  useEffect(() => {
    if (hasFetched.current) return; // Skip second StrictMode call
    hasFetched.current = true;

    const fetchFiles = async () => {
      try {
        setLoading(true);
        const filesData = await fileService.getAllFiles();
        setFiles(filesData);
        localStorage.setItem("files", JSON.stringify(filesData));
      } catch (error) {
        console.error("Failed to fetch files:", error);
        // Fallback to localStorage if API fails
        const cachedFiles = JSON.parse(localStorage.getItem("files") || "[]");
        setFiles(cachedFiles);
      } finally {
        setLoading(false);
      }
    };

    fetchFiles();
  }, []);

  const saveToStorage = (updatedFiles, updatedFolders) => {
    localStorage.setItem("files", JSON.stringify(updatedFiles));
    localStorage.setItem("folders", JSON.stringify(updatedFolders));
  };

  const handleFileSelect = (event) => {
    const files = Array.from(event.target.files);
    if (files.length > 0) {
      setSelectedFiles(files);
      setShowUploadModal(true);
    }
  };

  const handleCancelUpload = (index) => {
    const controller = abortControllersRef.current[index];
    if (controller) {
      controller.abort();
      delete abortControllersRef.current[index];
    }
  };

  const handleFileUpload = async (filesWithNewNames, onProgress) => {
    // Close modal immediately and seed the toast with 0% entries
    setShowUploadModal(false);
    setSelectedFiles([]);
    const initialQueue = filesWithNewNames.map((item, i) => ({
      id: i,
      name: item.newName,
      progress: 0,
      status: "uploading",
    }));
    setUploadQueue(initialQueue);
    abortControllersRef.current = {};

    const updateToast = (index, progress, status = "uploading") => {
      setUploadQueue((prev) =>
        prev.map((u) => (u.id === index ? { ...u, progress, status } : u)),
      );
    };

    const uploadedFiles = [];

    for (let i = 0; i < filesWithNewNames.length; i++) {
      const item = filesWithNewNames[i];
      const controller = new AbortController();
      abortControllersRef.current[i] = controller;

      try {
        let fileId, objectKey;

        if (fileService.isMultipart(item.originalFile)) {
          // ── Multipart upload for files > 10 MB ──
          const result = await fileService.multipartUpload(
            item.originalFile,
            ownerId,
            (progress) => {
              onProgress(i, progress);
              updateToast(i, progress);
            },
            controller.signal,
          );
          fileId = result.fileId;
          objectKey = result.objectKey;
        } else {
          // ── Regular single presigned URL upload ──
          onProgress(i, 10);
          updateToast(i, 10);

          const presignResponse = await fileService.getPresignedUrl(
            item.originalFile,
            ownerId,
            controller.signal,
          );

          onProgress(i, 30);
          updateToast(i, 30);

          await fileService.uploadToPresignedUrl(
            presignResponse.uploadUrl,
            item.originalFile,
            controller.signal,
          );

          onProgress(i, 90);
          updateToast(i, 90);

          fileId = presignResponse.fileId;
          objectKey = presignResponse.objectKey;
        }

        const newFile = {
          id: fileId,
          name: item.newName,
          size: item.originalFile.size,
          type: item.originalFile.type || "application/octet-stream",
          uploadedAt: new Date().toISOString(),
          folderId: currentFolder,
          objectKey,
          status: "Uploaded",
        };

        uploadedFiles.push(newFile);
        onProgress(i, 100);
        updateToast(i, 100, "done");
        delete abortControllersRef.current[i];
      } catch (error) {
        if (error.name === "AbortError") {
          console.log(`Upload cancelled: ${item.newName}`);
          updateToast(i, 0, "cancelled");
        } else {
          console.error(`Failed to upload ${item.newName}:`, error);
          updateToast(i, 0, "error");
        }
      }
    }

    // Update state with all successfully uploaded files
    if (uploadedFiles.length > 0) {
      const updatedFiles = [...files, ...uploadedFiles];
      setFiles(updatedFiles);
      saveToStorage(updatedFiles, folders);
    }

    // Auto-dismiss toast after 4 seconds when all done
    setTimeout(() => setUploadQueue([]), 4000);
  };

  const handleCreateFolder = (folderName) => {
    const newFolder = {
      id: Date.now(),
      name: folderName,
      createdAt: new Date().toISOString(),
      parentId: currentFolder,
    };

    const updatedFolders = [...folders, newFolder];
    setFolders(updatedFolders);
    saveToStorage(files, updatedFolders);
  };

  const handleDeleteFile = (fileId) => {
    const file = files.find((f) => f.id === fileId);
    setConfirmDelete({
      type: "file",
      id: fileId,
      name: file?.name || "this file",
    });
  };

  const handleDeleteFolder = (folderId) => {
    const folder = folders.find((f) => f.id === folderId);
    setConfirmDelete({
      type: "folder",
      id: folderId,
      name: folder?.name || "this folder",
    });
  };

  const handleConfirmDelete = async () => {
    if (!confirmDelete) return;
    const { type, id } = confirmDelete;
    setConfirmDelete(null);

    if (type === "file") {
      try {
        await fileService.deleteFile(id);
        const updatedFiles = files.filter((f) => f.id !== id);
        setFiles(updatedFiles);
        saveToStorage(updatedFiles, folders);
      } catch (error) {
        console.error("Failed to delete file:", error);
      }
    } else {
      const updatedFolders = folders.filter((f) => f.id !== id);
      const updatedFiles = files.filter((f) => f.folderId !== id);
      setFolders(updatedFolders);
      setFiles(updatedFiles);
      saveToStorage(updatedFiles, updatedFolders);
    }
  };

  const handleDownloadFile = async (file) => {
    try {
      // Get presigned download URL from API (with auth)
      const downloadUrl = await fileService.downloadFile(file.id);

      // Open presigned URL directly
      window.location.href = downloadUrl;
    } catch (error) {
      console.error("Download failed:", error);
      alert("Failed to download file: " + error.message);
    }
  };

  const getRecentFiles = () => {
    return [...files]
      .sort((a, b) => new Date(b.uploadedAt) - new Date(a.uploadedAt))
      .slice(0, 5);
  };

  const getCurrentFolderFiles = () => {
    return files.filter((f) => f.folderId === currentFolder);
  };

  const getCurrentSubfolders = () => {
    return folders.filter((f) => f.parentId === currentFolder);
  };

  return (
    <div className={styles.dashboard}>
      <Header />

      <main className={styles.main}>
        <div className={styles.toolbar}>
          <div className={styles.breadcrumb}>
            {currentFolder && (
              <button
                onClick={() => setCurrentFolder(null)}
                className={styles.backBtn}
              >
                ← Back
              </button>
            )}
            <span className={styles.path}>
              {currentFolder
                ? folders.find((f) => f.id === currentFolder)?.name
                : "Home"}
            </span>
          </div>

          <div className={styles.actions}>
            <label className={styles.actionBtn}>
              📤 Upload File
              <input
                type="file"
                multiple
                onChange={handleFileSelect}
                style={{ display: "none" }}
              />
            </label>
          </div>
        </div>

        {!currentFolder && getRecentFiles().length > 0 && (
          <section className={styles.section}>
            <h3>Recent Files</h3>
            <FileList
              files={getRecentFiles()}
              folders={[]}
              onFileDelete={handleDeleteFile}
              onFileDownload={handleDownloadFile}
              onFolderClick={() => {}}
            />
          </section>
        )}

        <section className={styles.section}>
          <h3>{currentFolder ? "Contents" : "All Files"}</h3>
          {loading ? (
            <div className={styles.loading}>Loading files...</div>
          ) : (
            <FileList
              files={getCurrentFolderFiles()}
              folders={getCurrentSubfolders()}
              onFileDelete={handleDeleteFile}
              onFolderDelete={handleDeleteFolder}
              onFileDownload={handleDownloadFile}
              onFolderClick={setCurrentFolder}
            />
          )}
        </section>

        {getCurrentFolderFiles().length === 0 &&
          getCurrentSubfolders().length === 0 && (
            <div className={styles.empty}>
              <p>No files yet</p>
              <p className={styles.hint}>Upload files to get started</p>
            </div>
          )}
      </main>

      <Modal
        isOpen={showFolderModal}
        onClose={() => setShowFolderModal(false)}
        onSubmit={handleCreateFolder}
        title="Create New Folder"
        placeholder="Enter folder name"
        submitText="Create"
      />
      <FileUploadModal
        isOpen={showUploadModal}
        onClose={() => {
          setShowUploadModal(false);
          setSelectedFiles([]);
        }}
        onSubmit={handleFileUpload}
        files={selectedFiles}
      />

      <UploadToast
        uploads={uploadQueue}
        onDismiss={() => setUploadQueue([])}
        onCancel={handleCancelUpload}
      />

      <ConfirmDialog
        isOpen={!!confirmDelete}
        title={`Delete ${confirmDelete?.type === "folder" ? "Folder" : "File"}`}
        message={`Are you sure you want to delete "${confirmDelete?.name}"? This action cannot be undone.`}
        confirmText="Delete"
        onConfirm={handleConfirmDelete}
        onCancel={() => setConfirmDelete(null)}
      />
    </div>
  );
};

export default Dashboard;
