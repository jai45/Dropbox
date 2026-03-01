import { useState } from "react";
import Header from "./Header";
import FileList from "./FileList";
import Modal from "./Modal";
import FileUploadModal from "./FileUploadModal";
import { fileService } from "../services/fileService";
import { useAuth } from "../contexts/AuthContext";
import styles from "./Dashboard.module.css";

const Dashboard = () => {
  const { user } = useAuth();
  const [files, setFiles] = useState(() => {
    return JSON.parse(localStorage.getItem("files") || "[]");
  });
  const [folders, setFolders] = useState(() => {
    return JSON.parse(localStorage.getItem("folders") || "[]");
  });
  const [currentFolder, setCurrentFolder] = useState(null);
  const [showFolderModal, setShowFolderModal] = useState(false);
  const [showUploadModal, setShowUploadModal] = useState(false);
  const [selectedFiles, setSelectedFiles] = useState([]);

  // Use hardcoded ownerId or derive from user email
  const ownerId = "a55f55f2-1fe4-4388-bab2-b9e1d4fe0e34";

  const saveToStorage = (newFiles, newFolders) => {
    localStorage.setItem("files", JSON.stringify(newFiles));
    localStorage.setItem("folders", JSON.stringify(newFolders));
  };

  const handleFileSelect = (e) => {
    const uploadedFiles = Array.from(e.target.files);
    if (uploadedFiles.length > 0) {
      setSelectedFiles(uploadedFiles);
      setShowUploadModal(true);
    }
    e.target.value = ""; // Reset input so same file can be selected again
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

  const handleDeleteFile = (fileId) => {
    const updatedFiles = files.filter((f) => f.id !== fileId);
    setFiles(updatedFiles);
    saveToStorage(updatedFiles, folders);
  };

  const handleDeleteFolder = (folderId) => {
    const updatedFolders = folders.filter((f) => f.id !== folderId);
    const updatedFiles = files.filter((f) => f.folderId !== folderId);
    setFolders(updatedFolders);
    setFiles(updatedFiles);
    saveToStorage(updatedFiles, updatedFolders);
  };

  const handleDownloadFile = (file) => {
    // Mock download - in real app, fetch from server
    const blob = new Blob(["Mock file content for " + file.name], {
      type: file.type,
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = file.name;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
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
            <button
              onClick={() => setShowFolderModal(true)}
              className={styles.actionBtn}
            >
              📁 New Folder
            </button>
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
          <h3>{currentFolder ? "Contents" : "All Files & Folders"}</h3>
          <FileList
            files={getCurrentFolderFiles()}
            folders={getCurrentSubfolders()}
            onFileDelete={handleDeleteFile}
            onFolderDelete={handleDeleteFolder}
            onFileDownload={handleDownloadFile}
            onFolderClick={setCurrentFolder}
          />
        </section>

        {getCurrentFolderFiles().length === 0 &&
          getCurrentSubfolders().length === 0 && (
            <div className={styles.empty}>
              <p>No files or folders yet</p>
              <p className={styles.hint}>
                Upload files or create folders to get started
              </p>
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
