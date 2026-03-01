# DropBox Clone - React Application

A minimalistic Dropbox-like file management application built with React and Vite.

## Features

- 🔐 **User Authentication** - Login with email and password
- 🌓 **Dark/Light Mode** - Toggle between themes with persistent preference
- 📁 **Folder Management** - Create and organize folders
- 📤 **File Upload** - Upload multiple files
- 📥 **File Download** - Download files with one click
- 🕐 **Recent Files** - Quick access to recently uploaded files
- 💾 **Local Storage** - All data persists in browser storage

## Tech Stack

- **React 19** - UI library
- **Vite** - Build tool and dev server
- **CSS Modules** - Component-scoped styling
- **No external UI libraries** - Pure React implementation

## Getting Started

### Prerequisites

- Node.js (v16 or higher)
- npm or yarn

### Installation

1. Install dependencies:

```bash
npm install
```

2. Start the development server:

```bash
npm run dev
```

3. Open your browser and navigate to `http://localhost:5173`

### Build for Production

```bash
npm run build
```

The production-ready files will be in the `dist` directory.

## Usage

1. **Login**: Enter any email and password to access the dashboard
2. **Upload Files**: Click "Upload File" button and select files
3. **Create Folders**: Click "New Folder" and enter a folder name
4. **Navigate**: Click on folders to browse their contents
5. **Download**: Click the download icon (⬇️) on any file
6. **Delete**: Click the delete icon (🗑️) to remove files or folders
7. **Theme Toggle**: Click the sun/moon icon in the header to switch themes

## Project Structure

```
src/
├── components/          # React components
│   ├── Dashboard.jsx    # Main dashboard view
│   ├── FileList.jsx     # File and folder list
│   ├── Header.jsx       # Top navigation bar
│   └── Login.jsx        # Authentication page
├── contexts/           # React contexts
│   ├── AuthContext.jsx  # Authentication state
│   └── ThemeContext.jsx # Theme management
├── App.jsx             # Root component
├── main.jsx            # Application entry point
└── index.css           # Global styles
```

## Features Implementation

### Authentication

- Simple mock authentication
- Credentials stored in localStorage
- Protected routes

### File Management

- Files and folders stored in localStorage
- Folder hierarchy support
- File metadata (name, size, type, upload date)

### Theme System

- CSS custom properties for theming
- Automatic persistence in localStorage
- Smooth transitions between themes

## Browser Support

- Chrome (latest)
- Firefox (latest)
- Safari (latest)
- Edge (latest)

## License

MIT
