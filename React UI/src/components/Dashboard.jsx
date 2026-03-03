import { useState, useEffect } from "react";
import Header from "./Header";
import FileList from "./FileList";
import Modal from "./Modal";
import FileUploadModal from "./FileUploadModal";
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

  // Use userId from authenticated user
  const ownerId = user?.userId;

  // Fetch files from API on mount
  useEffect(() => {
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

  const handleFileUpload = async (filesWithNewNames, onProgress) => {
    const uploadedFiles = [];

    for (let i = 0; i < filesWithNewNames.length; i++) {
      const item = filesWithNewNames[i];

      try {
        // Update progress: Getting presigned URL
        onProgress(i, 10);

        // Step 1: Get presigned URL
        const presignResponse = await fileService.getPresignedUrl(
          item.originalFile,
          ownerId,
        );

        onProgress(i, 30);

        // Step 2: Upload file to presigned URL
        await fileService.uploadToPresignedUrl(
          presignResponse.uploadUrl,
          item.originalFile,
        );

        onProgress(i, 90);

        // Step 3: Add to local state
        const newFile = {
          id: presignResponse.fileId,
          name: item.newName,
          size: item.originalFile.size,
          type: item.originalFile.type || "application/octet-stream",
          uploadedAt: new Date().toISOString(),
          folderId: currentFolder,
          objectKey: presignResponse.objectKey,
          status: "Uploaded",
        };

        uploadedFiles.push(newFile);
        onProgress(i, 100);
      } catch (error) {
        console.error(`Failed to upload ${item.newName}:`, error);
        throw new Error(`Failed to upload ${item.newName}: ${error.message}`);
      }
    }

    // Update state with all uploaded files
    const updatedFiles = [...files, ...uploadedFiles];
    setFiles(updatedFiles);
    saveToStorage(updatedFiles, folders);
    setSelectedFiles([]);
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

  const handleDeleteFile = async (fileId) => {
    try {
      // Call delete API
      await fileService.deleteFile(fileId);

      // Update local state after successful deletion
      const updatedFiles = files.filter((f) => f.id !== fileId);
      setFiles(updatedFiles);
      saveToStorage(updatedFiles, folders);
    } catch (error) {
      console.error("Failed to delete file:", error);
      alert("Failed to delete file: " + error.message);
    }
  };

  const handleDeleteFolder = (folderId) => {
    const updatedFolders = folders.filter((f) => f.id !== folderId);
    const updatedFiles = files.filter((f) => f.folderId !== folderId);
    setFolders(updatedFolders);
    setFiles(updatedFiles);
    saveToStorage(updatedFiles, updatedFolders);
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
    </div>
  );
};

export default Dashboard;
